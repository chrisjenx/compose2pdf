package com.chrisjenx.compose2pdf.internal

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.app.Presentation
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chrisjenx.compose2pdf.LocalPdfPageConfig
import com.chrisjenx.compose2pdf.PdfPageConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages off-screen Compose rendering on Android using a [VirtualDisplay] + [Presentation].
 *
 * [ComposeView] requires window attachment to start composition. This renderer
 * creates a headless virtual display (no Surface, no permissions required) and
 * hosts a [Presentation] on it to provide a real Window context. This triggers
 * composition without displaying anything on screen.
 *
 * After rendering, call [close] to release resources.
 */
internal class OffScreenComposeRenderer(
    private val context: Context,
) : AutoCloseable {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var lifecycleOwner: OffScreenLifecycleOwner? = null

    /**
     * Renders composable content off-screen and returns the measured [ComposeView].
     *
     * All Compose/lifecycle operations are dispatched to the main looper via [Handler].
     * The returned view is composed, measured, and laid out — ready for [View.draw].
     */
    suspend fun render(
        widthPx: Int,
        density: Density,
        config: PdfPageConfig,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): ComposeView = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            try {
                val owner = OffScreenLifecycleOwner()
                owner.initialize()
                lifecycleOwner = owner

                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val vd = dm.createVirtualDisplay(
                    "compose2pdf",
                    widthPx.coerceAtLeast(1),
                    1,
                    context.resources.displayMetrics.densityDpi,
                    null,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                )
                virtualDisplay = vd

                val pres = Presentation(context, vd.display)
                presentation = pres

                // Set lifecycle owners on a FrameLayout wrapper. ComposeView traverses
                // UP its parent tree to find these owners.
                val wrapper = FrameLayout(context).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                }

                val composeView = ComposeView(context).apply {
                    setContent {
                        CompositionLocalProvider(
                            LocalDensity provides density,
                            LocalPdfPageConfig provides config,
                        ) {
                            content()
                        }
                    }
                }

                wrapper.addView(
                    composeView,
                    FrameLayout.LayoutParams(widthPx, FrameLayout.LayoutParams.WRAP_CONTENT),
                )
                pres.setContentView(wrapper)

                // Listen for first layout (composition complete)
                composeView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            // Re-measure with unconstrained height
                            composeView.measure(
                                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                            )
                            composeView.layout(
                                0, 0,
                                composeView.measuredWidth, composeView.measuredHeight,
                            )
                            continuation.resume(composeView)
                        }
                    },
                )

                // show() triggers window attachment → composition → layout
                pres.show()
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post { close() }
        }
    }

    override fun close() {
        // Lifecycle/Presentation operations must happen on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            closeOnMainThread()
        } else {
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                closeOnMainThread()
                latch.countDown()
            }
            latch.await()
        }
    }

    private fun closeOnMainThread() {
        presentation?.dismiss()
        virtualDisplay?.release()
        lifecycleOwner?.destroy()
        presentation = null
        virtualDisplay = null
        lifecycleOwner = null
    }
}

/**
 * Minimal lifecycle + saved-state owner for off-screen Compose rendering.
 * [initialize] must be called on the main thread before use.
 */
private class OffScreenLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** Must be called on the main thread. */
    fun initialize() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

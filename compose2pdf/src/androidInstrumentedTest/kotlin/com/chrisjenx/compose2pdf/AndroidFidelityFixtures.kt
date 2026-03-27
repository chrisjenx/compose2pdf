package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.fixtures.SharedFixture
import com.chrisjenx.compose2pdf.fixtures.sharedFixtures

/**
 * Android fidelity fixtures — delegates entirely to the shared test-fixtures module.
 * This ensures JVM and Android render the exact same composable content.
 */
val androidFidelityFixtures: List<SharedFixture> = sharedFixtures

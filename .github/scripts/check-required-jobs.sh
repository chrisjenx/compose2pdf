#!/usr/bin/env bash
#
# Fail unless every "<job-name>=<result>" pair reports "success".
#
# Backs the compatibility workflow's `compat-gate` job: a single required status
# check that can't be masked by the non-blocking pre-release matrix cell. The job
# results (success / failure / cancelled / skipped) are passed in from the GitHub
# `needs.<job>.result` context. Anything other than "success" fails the gate.
#
# Runnable and testable locally:
#
#   .github/scripts/check-required-jobs.sh a=success b=success   # prints OK, exit 0
#   .github/scripts/check-required-jobs.sh a=success b=failure   # errors,    exit 1
#   .github/scripts/check-required-jobs.sh a=success b=skipped   # errors,    exit 1
#
# No `set -e`: we want to report ALL non-success jobs, then exit non-zero once.
set -uo pipefail

[ "$#" -gt 0 ] || {
  echo "usage: $(basename "$0") <name>=<result> [<name>=<result> ...]" >&2
  exit 2
}

rc=0
for pair in "$@"; do
  name="${pair%%=*}"
  result="${pair#*=}"
  printf '  %-20s %s\n' "${name}:" "$result"
  if [ "$result" != "success" ]; then
    echo "::error::Required job '${name}' did not succeed (result: ${result})."
    rc=1
  fi
done

if [ "$rc" -eq 0 ]; then
  echo "All required jobs succeeded."
fi
exit "$rc"

/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

/**
 * Keeps [BaseTest.runTest] from retrying deterministic failures.
 *
 * Assertion failures are deterministic, so retrying them cannot turn them green — it only hides a
 * real bug behind a "flaky" verdict. Once one is recorded, [failFast] re-throws it instead of
 * running the test body again, so the failure is reported by the first attempt that produced it.
 *
 * Only failures passed to [record] are classified, which must be exactly those thrown by the test
 * body. The timeout error of `runTest` is an [AssertionError] as well, but is worth retrying, and
 * stays retryable because it is thrown around the body rather than by it.
 */
internal class DeterministicFailureGuard {
    private var failure: Throwable? = null

    /** Re-throws the recorded failure, if any, to skip another attempt of the test body. */
    fun failFast() {
        failure?.let { throw it }
    }

    /** Records [cause] as deterministic when it is an [AssertionError]. */
    fun record(cause: Throwable) {
        if (cause is AssertionError) failure = cause
    }
}

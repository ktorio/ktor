/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Keeps a retry loop from retrying deterministic failures.
 *
 * Assertion failures are deterministic, so retrying them cannot turn them green — it only hides a
 * real bug behind a "flaky" verdict. Once one is recorded, [hasFailure] tells the enclosing retry
 * loop to stop — as `retryTest`'s `shouldRetry` predicate, or as part of a `recover` callback's
 * decision whether to rethrow.
 *
 * Only failures passed to [record] are classified, which must be exactly those thrown by the test
 * body. The timeout error of `runTest` is an [AssertionError] as well, but is worth retrying, and
 * stays retryable because it is thrown around the body rather than by it.
 * Used by [runTestWithData] and by `BaseTest.runTest`.
 */
class DeterministicFailureGuard {
    private var failure: Throwable? = null

    /** `true` once a deterministic failure has been recorded, so further attempts are pointless. */
    val hasFailure: Boolean get() = failure != null

    /** Records [cause] as deterministic when it is an [AssertionError]. */
    fun record(cause: Throwable) {
        if (cause is AssertionError) failure = cause
    }
}

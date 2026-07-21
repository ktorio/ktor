/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Test runner for js suspend tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.dispatcher.testSuspend)
 */
public actual fun testSuspend(
    context: CoroutineContext,
    timeoutMillis: Long,
    block: suspend CoroutineScope.() -> Unit
): TestResult = runTest(context = context, timeout = timeoutMillis.toDuration(DurationUnit.MILLISECONDS)) {
    block()
}

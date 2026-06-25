/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.dispatcher

import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds

public actual fun testSuspend(
    context: kotlin.coroutines.CoroutineContext,
    timeoutMillis: Long,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
): kotlinx.coroutines.test.TestResult = runTest(context = context, timeout = timeoutMillis.milliseconds) {
    block()
}

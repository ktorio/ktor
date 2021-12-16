/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias TestResult = Unit

/**
 * Test runner for jvm suspend tests.
 */
public actual fun testSuspend(
    context: CoroutineContext,
    dispatchTimeoutMs: Long,
    testBody: suspend TestScope.() -> Unit
) {
    runBlocking(context) {
        withTimeout(dispatchTimeoutMs) { TestScope().testBody() }
    }
}

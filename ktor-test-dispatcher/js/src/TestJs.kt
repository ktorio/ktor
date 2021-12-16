/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.js.*

/**
 * Test runner for js suspend tests.
 */
@OptIn(DelicateCoroutinesApi::class)
public actual fun testSuspend(
    context: CoroutineContext,
    dispatchTimeoutMs: Long,
    testBody: suspend TestScope.() -> Unit
): TestResult = GlobalScope.promise(block = {
    withTimeout(dispatchTimeoutMs) { TestScope().testBody() }
}, context = context)

@Suppress("ACTUAL_WITHOUT_EXPECT", "ACTUAL_TYPE_ALIAS_TO_CLASS_WITH_DECLARATION_SITE_VARIANCE")
public actual typealias TestResult = Promise<Unit>

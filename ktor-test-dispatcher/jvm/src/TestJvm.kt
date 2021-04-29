/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Test runner for jvm suspend tests.
 */
public actual fun testSuspend(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> Unit
): Unit = runBlocking(context, block)

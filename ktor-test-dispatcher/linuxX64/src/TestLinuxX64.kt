package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Test runner for native suspend tests.
 */
actual fun testSuspend(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> Unit
): Unit = runBlocking(context, block)

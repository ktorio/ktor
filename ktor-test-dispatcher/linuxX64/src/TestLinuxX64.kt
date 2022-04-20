package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.*

/**
 * Test runner for native suspend tests.
 */
public actual fun testSuspend(
    context: CoroutineContext,
    timeoutMillis: Long,
    block: suspend CoroutineScope.() -> Unit
) {
    executeInWorker(timeoutMillis) {
        runBlocking {
            block()
        }
    }
}

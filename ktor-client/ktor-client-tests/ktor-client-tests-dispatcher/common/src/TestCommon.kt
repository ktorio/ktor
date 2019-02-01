package io.ktor.client.tests.utils.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Test runner for common suspend tests.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect fun testSuspend(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
)

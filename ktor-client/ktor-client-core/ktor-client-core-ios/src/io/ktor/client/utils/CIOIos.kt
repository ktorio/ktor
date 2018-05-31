package io.ktor.client.utils

import kotlinx.coroutines.experimental.*

/**
 * Number of threads used for http clients: A little less than the [cpuCount] and 2 at least.
 */
actual val HTTP_CLIENT_THREAD_COUNT: Int = 0

/**
 * Default [IOCoroutineDispatcher] that uses [HTTP_CLIENT_THREAD_COUNT] as the number of threads.
 */
actual val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher = Unconfined

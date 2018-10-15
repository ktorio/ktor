package io.ktor.client.utils

import kotlinx.coroutines.*

/**
 * Ios http client use the main queue to execute request.
 */
actual val HTTP_CLIENT_THREAD_COUNT: Int = 0

/**
 * Default [IOCoroutineDispatcher] that uses [HTTP_CLIENT_THREAD_COUNT] as the number of threads.
 */
actual val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher = Dispatchers.Unconfined

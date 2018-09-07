package io.ktor.client.utils

import kotlinx.coroutines.*

/**
 * Ios http client use the main queue to execute request.
 */
@Deprecated(
    "HTTP_CLIENT_THREAD_COUNT is deprecated. Use [HttpClientEngineConfig.threadsCount] instead.",
    level = DeprecationLevel.ERROR
)
actual val HTTP_CLIENT_THREAD_COUNT: Int = 0

/**
 * Default [IOCoroutineDispatcher] that uses [HTTP_CLIENT_THREAD_COUNT] as the number of threads.
 */
@Deprecated(
    "HTTP_CLIENT_DEFAULT_DISPATCHER is deprecated. Use [HttpClient.coroutineContext] instead.",
    level = DeprecationLevel.ERROR
)
actual val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher = Dispatchers.Default

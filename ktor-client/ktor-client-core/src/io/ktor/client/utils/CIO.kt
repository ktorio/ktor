package io.ktor.client.utils

import kotlinx.coroutines.experimental.*

/**
 * Maximum number of buffers to be allocated in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_POOL_SIZE: Int = 1000

/**
 * Size of each buffer in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_BUFFER_SIZE: Int = 4096

/**
 * Number of threads used for http clients: A little less than the [cpuCount] and 2 at least.
 */
expect val HTTP_CLIENT_THREAD_COUNT: Int

/**
 * Default [IOCoroutineDispatcher] that uses [HTTP_CLIENT_THREAD_COUNT] as the number of threads.
 */
expect val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher

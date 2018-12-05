package io.ktor.client.utils

import kotlinx.coroutines.*

/**
 * Maximum number of buffers to be allocated in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_POOL_SIZE: Int = 1000

/**
 * Size of each buffer in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_BUFFER_SIZE: Int = 4096

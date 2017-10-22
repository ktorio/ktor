package io.ktor.util

import org.slf4j.*

/**
 * Logs an error from an [exception] using its message
 */
fun Logger.error(exception: Throwable) = error(exception.message ?: "Exception of type ${exception.javaClass}", exception)

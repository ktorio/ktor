package org.jetbrains.ktor.util

import org.slf4j.*

fun Logger.error(exception: Throwable) = error(exception.message ?: "Exception of type ${exception.javaClass}", exception)

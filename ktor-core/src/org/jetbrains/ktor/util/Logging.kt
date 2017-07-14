package org.jetbrains.ktor.util

import org.slf4j.*

fun Logger.error(exception: Throwable) = error(exception.message ?: "Exception of type ${exception.javaClass}", exception)

@Deprecated("Use warn instead", replaceWith = ReplaceWith("warn(message)"))
fun Logger.warning(message: String) = warn(message)

package io.ktor.utils.io.errors

actual open class IOException actual constructor(message: String, cause: Throwable?) : Exception(message, cause) {
    actual constructor(message: String) : this(message, null)
}

actual open class EOFException actual constructor(message: String) : IOException(message)

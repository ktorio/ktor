package io.ktor.client.features.logging


/**
 * [HttpClient] Logger.
 */
interface Logger {
    /**
     * Add [message] to log.
     */
    fun log(message: String)

    companion object
}

/**
 * Default logger to use.
 */
expect val Logger.Companion.DEFAULT: Logger

/**
 * [Logger] using [println].
 */
val Logger.Companion.SIMPLE: Logger get() = SimpleLogger()

/**
 * Empty [Logger] for test purpose.
 */
val Logger.Companion.EMPTY: Logger
    get() = object : Logger {
        override fun log(message: String) {}
    }

private class SimpleLogger : Logger {
    override fun log(message: String) {
        println("HttpClient: $message")
    }
}

package org.jetbrains.ktor.logging

/**
 * Provides unified interface to application logging.
 */
interface ApplicationLog {
    /**
     * Name of the log
     */
    val name: String

    /**
     * Sends information message to the log
     */
    fun info(message: String) {}

    /**
     * Sends debug message to the log
     */
    fun debug(message: String) {}

    /**
     * Sends error message to the log
     */
    fun error(message: String, exception: Throwable? = null) {}

    /**
     * Sends error message to the log, extracting message from [exception]
     */
    fun error(exception: Throwable) = error(exception.message ?: "Exception of type ${exception.javaClass}", exception)

    /**
     * Sends warning message to the log
     */
    fun warning(message: String) {}

    /**
     * Sends trace message to the log
     */
    fun trace(message: String) {}

    /**
     * Creates a detail fork of current log, using same underlying mechanism
     */
    fun fork(name: String): ApplicationLog
}


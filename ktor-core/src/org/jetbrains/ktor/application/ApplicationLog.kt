package org.jetbrains.ktor.application

import org.slf4j.*
import org.slf4j.Logger
import java.util.logging.*

/**
 * Provides unified interface to application logging.
 */
public interface ApplicationLog {
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

/**
 * Implements [ApplicationLog] by doing nothing
 */
public class NullApplicationLog(override val name: String = "Application") : ApplicationLog {
    override fun fork(name: String): ApplicationLog = NullApplicationLog("${this.name}.$name")
}

/**
 * Implements [ApplicationLog] by delegating to SLF4J [Logger]
 */
public class SLF4JApplicationLog(override val name: String) : ApplicationLog {
    private val logger: Logger = LoggerFactory.getLogger("$name")

    override fun info(message: String) = logger.info(message)
    override fun debug(message: String) = logger.debug(message)
    override fun error(message: String, exception: Throwable?) = if (exception != null)
        logger.error(message, exception)
    else
        logger.error(message)

    override fun warning(message: String) = logger.warn(message)
    override fun trace(message: String) = logger.trace(message)
    override fun fork(name: String): ApplicationLog = SLF4JApplicationLog("${this.name}.$name")
}

/**
 * Implements [ApplicationLog] by delegating to [java.util.logging.Logger]
 */
public class JULApplicationLog(override val name: String) : ApplicationLog {
    private val logger = java.util.logging.Logger.getLogger(name)

    override fun info(message: String) = logger.log(Level.INFO, message)
    override fun debug(message: String) = logger.log(Level.FINEST, message)
    override fun error(message: String, exception: Throwable?) = if (exception != null)
        logger.log(Level.SEVERE, message, exception)
    else
        logger.log(Level.SEVERE, message)

    override fun warning(message: String) = logger.log(Level.WARNING, message)
    override fun trace(message: String) = logger.log(Level.FINE, message)
    override fun fork(name: String): ApplicationLog = JULApplicationLog("${this.name}.$name")
}

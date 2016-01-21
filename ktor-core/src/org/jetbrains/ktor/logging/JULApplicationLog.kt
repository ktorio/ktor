package org.jetbrains.ktor.logging

import java.util.logging.*

/**
 * Implements [ApplicationLog] by delegating to [java.util.logging.Logger]
 */
public class JULApplicationLog(override val name: String) : ApplicationLog {
    private val logger = Logger.getLogger(name)

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
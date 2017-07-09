package org.jetbrains.ktor.logging

import org.slf4j.*

/**
 * Implements [ApplicationLog] by delegating to SLF4J [Logger]
 */
class SLF4JApplicationLog(override val name: String) : ApplicationLog {
    private val logger: Logger = LoggerFactory.getLogger(name)

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
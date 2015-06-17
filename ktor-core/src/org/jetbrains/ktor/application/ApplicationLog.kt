package org.jetbrains.ktor.application

import org.slf4j.*

public interface ApplicationLog {
    fun info(message : String) {}
    fun debug(message : String) {}
    fun error(message : String, exception: Throwable? = null) {}
    fun warning(message : String) {}
    fun trace(message: String) {}

    fun error(exception: Throwable) = error(exception.getMessage() ?: "Exception of type ${exception.javaClass}", exception)
}

public class NullApplicationLog : ApplicationLog {}

public class SL4JApplicationLog(name: String) : ApplicationLog {
    val logger: Logger = LoggerFactory.getLogger(name)

    override fun info(message: String) {
        logger.info(message)
    }

    override fun debug(message: String) {
        logger.debug(message)
    }

    override fun error(message: String, exception: Throwable?) {
        if (exception != null)
            logger.error(message, exception)
        else
            logger.error(message)
    }

    override fun warning(message: String) {
        logger.warn(message)
    }

    override fun trace(message: String) {
        logger.trace(message)
    }
}

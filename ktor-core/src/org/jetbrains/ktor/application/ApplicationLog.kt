package org.jetbrains.ktor.application

import org.slf4j.*
import org.slf4j.Logger
import java.util.logging.*

public interface ApplicationLog {
    val name: String

    fun info(message: String) {}
    fun debug(message: String) {}
    fun error(message: String, exception: Throwable? = null) {}
    fun warning(message: String) {}
    fun trace(message: String) {}

    fun error(exception: Throwable) = error(exception.getMessage() ?: "Exception of type ${exception.javaClass}", exception)

    fun fork(name: String): ApplicationLog
}

public class NullApplicationLog(override val name: String = "Application") : ApplicationLog {
    override fun fork(name: String): ApplicationLog {
        return NullApplicationLog("${this.name}.$name")
    }
}

public class SL4JApplicationLog(override val name: String) : ApplicationLog {
    private val logger: Logger = LoggerFactory.getLogger("$name")

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

    override fun fork(name: String): ApplicationLog {
        return SL4JApplicationLog("${this.name}.$name")
    }
}

public class JULApplicationLog(override val name: String) : ApplicationLog {
    private val logger = java.util.logging.Logger.getLogger(name)

    override fun info(message: String) {
        logger.log(Level.INFO, message)
    }

    override fun debug(message: String) {
        logger.log(Level.FINEST, message)
    }

    override fun error(message: String, exception: Throwable?) {
        if (exception != null)
            logger.log(Level.SEVERE, message, exception)
        else
            logger.log(Level.SEVERE, message)
    }

    override fun warning(message: String) {
        logger.log(Level.WARNING, message)
    }

    override fun trace(message: String) {
        logger.log(Level.FINE, message)
    }

    override fun fork(name: String): ApplicationLog {
        return JULApplicationLog("${this.name}.$name")
    }
}

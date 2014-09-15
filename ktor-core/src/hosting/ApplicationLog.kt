package ktor.application

import org.slf4j.*

public trait ApplicationLog {
    public fun info(message : String) {}
    public fun debug(message : String) {}
    public fun error(message : String) {}
    public fun warning(message : String) {}
}

public class NullApplicationLog : ApplicationLog {}

public class SL4JApplicationLog(name : String) : ApplicationLog {
    val logger = LoggerFactory.getLogger(name)!!

    override fun info(message: String) {
        logger.info(message)
    }
    override fun debug(message: String) {
        logger.debug(message)
    }
    override fun error(message: String) {
        logger.error(message)
    }
    override fun warning(message: String) {
        logger.warn(message)
    }
}

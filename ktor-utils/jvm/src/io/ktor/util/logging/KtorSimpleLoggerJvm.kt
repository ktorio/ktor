/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import org.slf4j.*

private val isAndroid = try {
    Class.forName("android.os.Build")
    true
} catch (_: ClassNotFoundException) {
    false
}

@Suppress("FunctionName")
public actual fun KtorSimpleLogger(name: String): Logger = if (!isAndroid) {
    LoggerFactory.getLogger(name)
} else {
    object : org.slf4j.Logger {
        private val logger by lazy {
            LoggerFactory.getLogger(name)
        }

        override fun getName(): String {
            return logger.name
        }

        override fun isTraceEnabled(): Boolean {
            return logger.isTraceEnabled
        }

        override fun trace(msg: String?) {
            logger.trace(msg)
        }

        override fun trace(format: String?, arg: Any?) {
            logger.trace(format, arg)
        }

        override fun trace(format: String?, arg1: Any?, arg2: Any?) {
            logger.trace(format, arg1, arg2)
        }

        override fun trace(format: String?, vararg arguments: Any?) {
            logger.trace(format, *arguments)
        }

        override fun trace(msg: String?, t: Throwable?) {
            logger.trace(msg, t)
        }

        override fun isTraceEnabled(marker: Marker?): Boolean {
            return logger.isTraceEnabled(marker)
        }

        override fun trace(marker: Marker?, msg: String?) {
            logger.trace(marker, msg)
        }

        override fun trace(marker: Marker?, format: String?, arg: Any?) {
            logger.trace(marker, format, arg)
        }

        override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            logger.trace(marker, format, arg1, arg2)
        }

        override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
            logger.trace(marker, format, *argArray)
        }

        override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
            logger.trace(marker, msg, t)
        }

        override fun isDebugEnabled(): Boolean {
            return logger.isDebugEnabled
        }

        override fun debug(msg: String?) {
            logger.debug(msg)
        }

        override fun debug(format: String?, arg: Any?) {
            logger.debug(format, arg)
        }

        override fun debug(format: String?, arg1: Any?, arg2: Any?) {
            logger.debug(format, arg1, arg2)
        }

        override fun debug(format: String?, vararg arguments: Any?) {
            logger.debug(format, *arguments)
        }

        override fun debug(msg: String?, t: Throwable?) {
            logger.debug(msg, t)
        }

        override fun isDebugEnabled(marker: Marker?): Boolean {
            return logger.isDebugEnabled(marker)
        }

        override fun debug(marker: Marker?, msg: String?) {
            logger.debug(marker, msg)
        }

        override fun debug(marker: Marker?, format: String?, arg: Any?) {
            logger.debug(marker, format, arg)
        }

        override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            logger.debug(marker, format, arg1, arg2)
        }

        override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
            logger.debug(marker, format, *arguments)
        }

        override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
            logger.debug(marker, msg, t)
        }

        override fun isInfoEnabled(): Boolean {
            return logger.isInfoEnabled
        }

        override fun info(msg: String?) {
            logger.info(msg)
        }

        override fun info(format: String?, arg: Any?) {
            logger.info(format, arg)
        }

        override fun info(format: String?, arg1: Any?, arg2: Any?) {
            logger.info(format, arg1, arg2)
        }

        override fun info(format: String?, vararg arguments: Any?) {
            logger.info(format, *arguments)
        }

        override fun info(msg: String?, t: Throwable?) {
            logger.info(msg, t)
        }

        override fun isInfoEnabled(marker: Marker?): Boolean {
            return logger.isInfoEnabled(marker)
        }

        override fun info(marker: Marker?, msg: String?) {
            logger.info(marker, msg)
        }

        override fun info(marker: Marker?, format: String?, arg: Any?) {
            logger.info(marker, format, arg)
        }

        override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            logger.info(marker, format, arg1, arg2)
        }

        override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
            logger.info(marker, format, *arguments)
        }

        override fun info(marker: Marker?, msg: String?, t: Throwable?) {
            logger.info(marker, msg, t)
        }

        override fun isWarnEnabled(): Boolean {
            return logger.isWarnEnabled
        }

        override fun warn(msg: String?) {
            logger.warn(msg)
        }

        override fun warn(format: String?, arg: Any?) {
            logger.warn(format, arg)
        }

        override fun warn(format: String?, vararg arguments: Any?) {
            logger.warn(format, *arguments)
        }

        override fun warn(format: String?, arg1: Any?, arg2: Any?) {
            logger.warn(format, arg1, arg2)
        }

        override fun warn(msg: String?, t: Throwable?) {
            logger.warn(msg, t)
        }

        override fun isWarnEnabled(marker: Marker?): Boolean {
            return logger.isWarnEnabled(marker)
        }

        override fun warn(marker: Marker?, msg: String?) {
            logger.warn(marker, msg)
        }

        override fun warn(marker: Marker?, format: String?, arg: Any?) {
            logger.warn(marker, format, arg)
        }

        override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            logger.warn(marker, format, arg1, arg2)
        }

        override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
            logger.warn(marker, format, *arguments)
        }

        override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
            logger.warn(marker, msg, t)
        }

        override fun isErrorEnabled(): Boolean {
            return logger.isErrorEnabled
        }

        override fun error(msg: String?) {
            logger.error(msg)
        }

        override fun error(format: String?, arg: Any?) {
            logger.error(format, arg)
        }

        override fun error(format: String?, arg1: Any?, arg2: Any?) {
            logger.error(format, arg1, arg2)
        }

        override fun error(format: String?, vararg arguments: Any?) {
            logger.error(format, *arguments)
        }

        override fun error(msg: String?, t: Throwable?) {
            logger.error(msg, t)
        }

        override fun isErrorEnabled(marker: Marker?): Boolean {
            return logger.isErrorEnabled(marker)
        }

        override fun error(marker: Marker?, msg: String?) {
            logger.error(marker, msg)
        }

        override fun error(marker: Marker?, format: String?, arg: Any?) {
            logger.error(marker, format, arg)
        }

        override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            logger.error(marker, format, arg1, arg2)
        }

        override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
            logger.error(marker, format, *arguments)
        }

        override fun error(marker: Marker?, msg: String?, t: Throwable?) {
            logger.error(marker, msg, t)
        }
    }
}

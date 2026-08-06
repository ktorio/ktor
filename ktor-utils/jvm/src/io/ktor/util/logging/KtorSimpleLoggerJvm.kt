/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import org.slf4j.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val loggerInitExecutor: Executor = ThreadPoolExecutor(
    0,
    1,
    30L,
    TimeUnit.SECONDS,
    LinkedBlockingQueue(),
) { r ->
    Thread(r, "ktor-slf4j-logger-init").apply { isDaemon = true }
}

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
        private val loggerFuture = CompletableFuture.supplyAsync({
            LoggerFactory.getLogger(name)
        }, loggerInitExecutor)

        override fun getName(): String {
            return name
        }

        override fun isTraceEnabled(): Boolean {
            return loggerFuture.getNow(null)?.isTraceEnabled ?: false
        }

        override fun trace(msg: String?) {
            loggerFuture.thenAccept { logger -> logger.trace(msg) }
        }

        override fun trace(format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(format, arg) }
        }

        override fun trace(format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(format, arg1, arg2) }
        }

        override fun trace(format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(format, *arguments) }
        }

        override fun trace(msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.trace(msg, t) }
        }

        override fun isTraceEnabled(marker: Marker?): Boolean {
            return loggerFuture.getNow(null)?.isTraceEnabled(marker) ?: false
        }

        override fun trace(marker: Marker?, msg: String?) {
            loggerFuture.thenAccept { logger -> logger.trace(marker, msg) }
        }

        override fun trace(marker: Marker?, format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(marker, format, arg) }
        }

        override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(marker, format, arg1, arg2) }
        }

        override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
            loggerFuture.thenAccept { logger -> logger.trace(marker, format, *argArray) }
        }

        override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.trace(marker, msg, t) }
        }

        override fun isDebugEnabled(): Boolean {
            return loggerFuture.getNow(null)?.isDebugEnabled ?: false
        }

        override fun debug(msg: String?) {
            loggerFuture.thenAccept { logger -> logger.debug(msg) }
        }

        override fun debug(format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(format, arg) }
        }

        override fun debug(format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(format, arg1, arg2) }
        }

        override fun debug(format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(format, *arguments) }
        }

        override fun debug(msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.debug(msg, t) }
        }

        override fun isDebugEnabled(marker: Marker?): Boolean {
            return loggerFuture.getNow(null)?.isDebugEnabled(marker) ?: false
        }

        override fun debug(marker: Marker?, msg: String?) {
            loggerFuture.thenAccept { logger -> logger.debug(marker, msg) }
        }

        override fun debug(marker: Marker?, format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(marker, format, arg) }
        }

        override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(marker, format, arg1, arg2) }
        }

        override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.debug(marker, format, *arguments) }
        }

        override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.debug(marker, msg, t) }
        }

        override fun isInfoEnabled(): Boolean {
            return loggerFuture.getNow(null)?.isInfoEnabled ?: false
        }

        override fun info(msg: String?) {
            loggerFuture.thenAccept { logger -> logger.info(msg) }
        }

        override fun info(format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(format, arg) }
        }

        override fun info(format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(format, arg1, arg2) }
        }

        override fun info(format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(format, *arguments) }
        }

        override fun info(msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.info(msg, t) }
        }

        override fun isInfoEnabled(marker: Marker?): Boolean {
            return loggerFuture.getNow(null)?.isInfoEnabled(marker) ?: false
        }

        override fun info(marker: Marker?, msg: String?) {
            loggerFuture.thenAccept { logger -> logger.info(marker, msg) }
        }

        override fun info(marker: Marker?, format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(marker, format, arg) }
        }

        override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(marker, format, arg1, arg2) }
        }

        override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.info(marker, format, *arguments) }
        }

        override fun info(marker: Marker?, msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.info(marker, msg, t) }
        }

        override fun isWarnEnabled(): Boolean {
            return loggerFuture.getNow(null)?.isWarnEnabled ?: false
        }

        override fun warn(msg: String?) {
            loggerFuture.thenAccept { logger -> logger.warn(msg) }
        }

        override fun warn(format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(format, arg) }
        }

        override fun warn(format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(format, *arguments) }
        }

        override fun warn(format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(format, arg1, arg2) }
        }

        override fun warn(msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.warn(msg, t) }
        }

        override fun isWarnEnabled(marker: Marker?): Boolean {
            return loggerFuture.getNow(null)?.isWarnEnabled(marker) ?: false
        }

        override fun warn(marker: Marker?, msg: String?) {
            loggerFuture.thenAccept { logger -> logger.warn(marker, msg) }
        }

        override fun warn(marker: Marker?, format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(marker, format, arg) }
        }

        override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(marker, format, arg1, arg2) }
        }

        override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.warn(marker, format, *arguments) }
        }

        override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.warn(marker, msg, t) }
        }

        override fun isErrorEnabled(): Boolean {
            return loggerFuture.getNow(null)?.isErrorEnabled ?: false
        }

        override fun error(msg: String?) {
            loggerFuture.thenAccept { logger -> logger.error(msg) }
        }

        override fun error(format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(format, arg) }
        }

        override fun error(format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(format, arg1, arg2) }
        }

        override fun error(format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(format, *arguments) }
        }

        override fun error(msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.error(msg, t) }
        }

        override fun isErrorEnabled(marker: Marker?): Boolean {
            return loggerFuture.getNow(null)?.isErrorEnabled(marker) ?: false
        }

        override fun error(marker: Marker?, msg: String?) {
            loggerFuture.thenAccept { logger -> logger.error(marker, msg) }
        }

        override fun error(marker: Marker?, format: String?, arg: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(marker, format, arg) }
        }

        override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(marker, format, arg1, arg2) }
        }

        override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
            loggerFuture.thenAccept { logger -> logger.error(marker, format, *arguments) }
        }

        override fun error(marker: Marker?, msg: String?, t: Throwable?) {
            loggerFuture.thenAccept { logger -> logger.error(marker, msg, t) }
        }
    }
}

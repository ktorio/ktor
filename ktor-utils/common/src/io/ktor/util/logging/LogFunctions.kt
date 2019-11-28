/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

fun Logger.trace(message: String, cause: Throwable? = null) {
    log(Level.TRACE) {
        this.text = message
        this.exception = cause
    }
}

fun Logger.trace(format: String, vararg args: Any?) {
    trace(message = legacyFormat(format, args))
}

fun Logger.trace(exception: Throwable) {
    trace(exception.messageOrName(), exception)
}

inline fun Logger.trace(cause: Throwable? = null, message: () -> String) {
    log(Level.TRACE) {
        this.text = message()
        this.exception = cause
    }
}

fun Logger.debug(message: String, cause: Throwable? = null) {
    log(Level.DEBUG) {
        this.text = message
        this.exception = cause
    }
}

inline fun Logger.debug(cause: Throwable? = null, message: () -> String) {
    log(Level.DEBUG) {
        this.text = message()
        this.exception = cause
    }
}

fun Logger.debug(format: String, vararg args: Any?) {
    debug(message = legacyFormat(format, args))
}

fun Logger.debug(exception: Throwable) {
    debug(exception.messageOrName(), exception)
}

fun Logger.info(message: String, cause: Throwable? = null) {
    log {
        this.text = message
        this.exception = cause
    }
}

inline fun Logger.info(cause: Throwable? = null, message: () -> String) {
    log(Level.INFO) {
        this.text = message()
        this.exception = cause
    }
}

fun Logger.info(format: String, vararg args: Any?) {
    info(message = legacyFormat(format, args))
}

fun Logger.info(exception: Throwable) {
    info(exception.messageOrName(), exception)
}

fun Logger.error(message: String, cause: Throwable? = null) {
    log(Level.ERROR) {
        this.text = message
        this.exception = cause
    }
}

inline fun Logger.error(cause: Throwable? = null, message: () -> String) {
    log(Level.ERROR) {
        this.text = message()
        this.exception = cause
    }
}

fun Logger.error(format: String, vararg args: Any?) {
    error(message = legacyFormat(format, args))
}

fun Logger.error(exception: Throwable) {
    error(exception.messageOrName(), exception)
}

@Deprecated("Use warning instead.", ReplaceWith("warning(message, cause)"))
fun Logger.warn(message: String, cause: Throwable? = null) {
    warning(message, cause)
}

fun Logger.warning(message: String, cause: Throwable? = null) {
    log(Level.WARNING) {
        this.text = message
        this.exception = cause
    }
}

inline fun Logger.warning(cause: Throwable? = null, message: () -> String) {
    log(Level.WARNING) {
        this.text = message()
        this.exception = cause
    }
}

@Deprecated("Use warning instead.", ReplaceWith("warning(format, *args)"))
fun Logger.warn(format: String, vararg args: Any?) {
    warning(format, *args)
}

fun Logger.warning(format: String, vararg args: Any?) {
    warning(message = legacyFormat(format, args))
}

fun Logger.warning(exception: Throwable) {
    warning(exception.messageOrName(), exception)
}

inline fun Logger.log(level: Level = Level.INFO, block: LogRecord.() -> Unit) {
    val event = begin(level) ?: return
    try {
        block(event)
        commit(event)
    } finally {
        event.release()
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Use info instead.", ReplaceWith("info(message)"))
fun Logger.log(message: String) {
    info(message)
}

private fun Throwable.messageOrName(): String = message ?: toString()


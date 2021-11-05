/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

public actual fun KtorSimpleLogger(name: String): Logger = object : Logger {
    override fun error(message: String) {
        console.error(message)
    }

    override fun error(message: String, cause: Throwable) {
        console.error("$message, cause: $cause")
    }

    override fun warn(message: String) {
        console.warn(message)
    }

    override fun warn(message: String, cause: Throwable) {
        console.warn("$message, cause: $cause")
    }

    override fun info(message: String) {
        console.info(message)
    }

    override fun info(message: String, cause: Throwable) {
        console.info("$message, cause: $cause")
    }

    override fun debug(message: String) {
        console.info("DEBUG: $message")
    }

    override fun debug(message: String, cause: Throwable) {
        console.info("DEBUG: $message, cause: $cause")
    }

    override fun trace(message: String) {
        console.info("TRACE: $message")
    }

    override fun trace(message: String, cause: Throwable) {
        console.info("TRACE: $message, cause: $cause")
    }
}

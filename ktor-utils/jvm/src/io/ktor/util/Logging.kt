/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import org.slf4j.*

/**
 * Logs an error from an [exception] using its message
 */
public fun Logger.error(exception: Throwable) {
    error(exception.message ?: "Exception of type ${exception.javaClass}", exception)
}

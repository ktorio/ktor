/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@InternalAPI
public actual typealias Logger = org.slf4j.Logger

@InternalAPI
public actual typealias LoggingLevel = org.slf4j.event.Level

@InternalAPI
public actual object LoggerFactory {
    public actual fun getLogger(name: String): Logger {
        return org.slf4j.LoggerFactory.getLogger(name)
    }
}

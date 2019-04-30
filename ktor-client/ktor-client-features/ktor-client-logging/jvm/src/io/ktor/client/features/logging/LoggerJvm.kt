/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.*
import org.slf4j.*

actual val Logger.Companion.DEFAULT: Logger
    get() = object : Logger {
        private val delegate = LoggerFactory.getLogger(HttpClient::class.java)!!
        override fun log(message: String) {
            delegate.info(message)
        }
    }

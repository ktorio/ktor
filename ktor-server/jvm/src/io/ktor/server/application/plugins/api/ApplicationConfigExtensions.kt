/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.config.*

/**
 * Port of the current application. Same as in config.
 **/
public val ApplicationConfig.port: Int get() = propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080

/**
 * Host of the current application. Same as in config.
 **/
public val ApplicationConfig.host: String get() = propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"

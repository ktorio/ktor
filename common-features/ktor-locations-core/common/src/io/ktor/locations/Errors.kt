/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

/**
 * Exception indicating that route parameters in curly brackets do not match class properties.
 */
@KtorExperimentalLocationsAPI
public class LocationRoutingException(message: String) : Exception(message)

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.locations

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Constructs a String with the url of a instance [location] whose class must be annotated with [Location].
 */
@KtorExperimentalLocationsAPI
public fun ApplicationCall.url(location: Any, block: URLBuilder.() -> Unit = {}): String = url {
    encodedParameters.clear()
    application.locations.href(location, this)
    block()
}

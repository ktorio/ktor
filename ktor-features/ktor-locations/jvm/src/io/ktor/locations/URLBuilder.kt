package io.ktor.locations

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Constructs a String with the url of a instance [location] whose class must be annotated with [Location].
 */
@KtorExperimentalLocationsAPI
fun ApplicationCall.url(location: Any, block: URLBuilder.() -> Unit = {}): String = url {
    parameters.clear()
    application.locations.href(location, this)
    block()
}


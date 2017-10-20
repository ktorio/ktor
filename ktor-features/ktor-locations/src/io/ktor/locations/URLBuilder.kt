package io.ktor.locations

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*

fun ApplicationCall.url(location: Any, block: URLBuilder.() -> Unit = {}): String = url {
    parameters.clear()
    application.locations.href(location, this)
    block()
}


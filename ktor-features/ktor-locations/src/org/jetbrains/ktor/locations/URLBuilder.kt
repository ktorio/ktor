package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.util.*
import org.jetbrains.ktor.util.*

fun ApplicationCall.url(location: Any, block: URLBuilder.() -> Unit = {}): String = url {
    parameters.clear()
    application.locations.href(location, this)
    block()
}


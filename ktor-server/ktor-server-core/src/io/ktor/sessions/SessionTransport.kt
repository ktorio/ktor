package io.ktor.sessions

import io.ktor.application.*

interface SessionTransport {
    fun receive(call: ApplicationCall): String?
    fun send(call: ApplicationCall, value: String)
    fun clear(call: ApplicationCall)
}


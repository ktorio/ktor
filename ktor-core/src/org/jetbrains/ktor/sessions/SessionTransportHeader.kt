package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.response.*

class SessionTransportHeader(val name: String) : SessionTransport {
    override fun receive(call: ApplicationCall): String? {
        return call.request.headers[name]
    }

    override fun send(call: ApplicationCall, value: String) {
        call.response.header(name, value)
    }

    override fun clear(call: ApplicationCall) {

    }
}
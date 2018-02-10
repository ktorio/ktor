package io.ktor.sessions

import io.ktor.application.*
import io.ktor.response.*

/**
 * SessionTransport that sets or gets the specific header [name],
 * applying/un-applying the specified transforms defined by [transformers].
 */
class SessionTransportHeader(val name: String,
                             val transformers: List<SessionTransportTransformer>
) : SessionTransport {
    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.headers[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        call.response.header(name, transformers.transformWrite(value))
    }

    override fun clear(call: ApplicationCall) {}
}
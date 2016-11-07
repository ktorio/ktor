package org.jetbrains.ktor.application

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*

/**
 * Represents server's response
 */
interface ApplicationResponse {
    val pipeline: RespondPipeline
    val headers: ResponseHeaders
    val cookies: ResponseCookies

    fun status(): HttpStatusCode?
    fun status(value: HttpStatusCode)

    @Deprecated("Use call.respond instead")
    fun channel(): WriteChannel

    /**
     * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
     * or does nothing (may call or not call [block]).
     * Exact behaviour is up to host implementation.
     */
    fun push(block: ResponsePushBuilder.() -> Unit) {
    }
}

/**
 * Respond with content producer. The function [body] will be called later when ktor ready with [Writer] instance
 * at receiver. You don't need to close it.
 * If you go async inside of [body] then you MUST handle exceptions properly and close provided [Writer] instance on you own
 * otherwise the response could get stuck.
 */
fun ApplicationCall.respondWrite(charset: Charset = Charsets.UTF_8, body: Writer.() -> Unit) : Nothing = respond(object : StreamConsumer() {
    override val headers: ValuesMap get() = ValuesMap.Empty

    override fun stream(out: OutputStream) {
        out.writer(charset).let { writer ->
            writer.body()
            writer.flush()
        }
    }
})

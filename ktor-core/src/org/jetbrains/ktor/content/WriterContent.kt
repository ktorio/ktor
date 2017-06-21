package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*

class WriterContent(private val body: suspend Writer.() -> Unit, private val charset: Charset) : FinalContent.WriteChannelContent() {
    override val headers: ValuesMap get() = ValuesMap.Empty

    override suspend fun writeTo(channel: WriteChannel) {
        val writer = channel.toOutputStream().writer(charset)
        writer.use {
            it.body()
        }
        channel.close()
    }
}


/**
 * Respond with content producer. The function [writer] will be called later when ktor ready with [Writer] instance
 * at receiver. You don't need to close it.
 * If you go async inside of [writer] then you MUST handle exceptions properly and close provided [Writer] instance on you own
 * otherwise the response could get stuck.
 */
suspend fun ApplicationCall.respondWrite(charset: Charset = Charsets.UTF_8, writer: suspend Writer.() -> Unit) {
    respond(WriterContent(writer, charset))
}

package io.ktor.html

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.html.*
import kotlinx.html.stream.*

/**
 * Responds to a client with a HTML response, using specified [block] to build an HTML page
 */
suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    respond(HtmlContent(status, block))
}

/**
 * Represents an [OutgoingContent] using `kotlinx.html` builder.
 */
class HtmlContent(override val status: HttpStatusCode? = null, private val builder: HTML.() -> Unit) : OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.bufferedWriter().use {
                    it.append("<!DOCTYPE html>\n")
                    it.appendHTML().html(block = builder)
                }
    }
}

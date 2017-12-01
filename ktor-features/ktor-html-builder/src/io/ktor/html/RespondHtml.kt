package io.ktor.html

import kotlinx.html.*
import kotlinx.html.stream.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.io.*

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    respond(HtmlContent(status, builder = block))
}

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK,
                                        versions: List<Version> = emptyList(),
                                        cacheControl: CacheControl? = null,
                                        block: HTML.() -> Unit) {
    respond(HtmlContent(status, versions, cacheControl, builder = block))
}

class HtmlContent(override val status: HttpStatusCode? = null,
                  override val versions: List<Version> = emptyList(),
                  override val cacheControl: CacheControl? = null,
                  val builder: HTML.() -> Unit) : Resource, OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val expires = null
    override val contentLength = null
    override val headers by lazy { super<Resource>.headers }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.bufferedWriter().use {
            it.append("<!DOCTYPE html>\n")
            it.appendHTML().html(builder)
        }
    }
}

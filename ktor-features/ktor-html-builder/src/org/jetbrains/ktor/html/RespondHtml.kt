package org.jetbrains.ktor.html

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

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
                  val builder: HTML.() -> Unit) : Resource, FinalContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val expires = null
    override val contentLength = null
    override val headers by lazy { super<Resource>.headers }

    override suspend fun writeTo(channel: WriteChannel) {
        val writer = channel.toOutputStream().bufferedWriter()
        writer.use {
            it.append("<!DOCTYPE html>\n")
            it.appendHTML().html(builder)
        }
        channel.close()
    }
}

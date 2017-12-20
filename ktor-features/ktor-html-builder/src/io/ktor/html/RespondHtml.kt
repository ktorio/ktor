package io.ktor.html

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.*

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    respond(HtmlContent(status, builder = block))
}

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK,
                                        versions: List<Version> = emptyList(),
                                        cacheControl: CacheControl? = null,
                                        block: HTML.() -> Unit) {
    respond(HtmlContent(status, versions, cacheControl = cacheControl, builder = block))
}

class HtmlContent(override val status: HttpStatusCode? = null,
                  override val versions: List<Version> = emptyList(),
                  val expires: LocalDateTime? = null,
                  val cacheControl: CacheControl? = null,
                  val builder: HTML.() -> Unit) : OutgoingContent.WriteChannelContent(), VersionedContent {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        ValuesMap.build(true) {
            contentType(contentType)
            expires?.let { expires(it) }
            cacheControl?.let { cacheControl(it) }
        }
    }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.bufferedWriter().use {
            it.append("<!DOCTYPE html>\n")
            it.appendHTML().html(builder)
        }
    }
}

package org.jetbrains.ktor.html

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import java.io.*

fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit): Nothing {
    respond(HtmlContent(status, builder = block))
}

fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK,
                                versions: List<Version> = emptyList(),
                                cacheControl: CacheControl? = null,
                                block: HTML.() -> Unit): Nothing {
    respond(HtmlContent(status, versions, cacheControl, builder = block))
}

class HtmlContent(override val status: HttpStatusCode? = null,
                  override val versions: List<Version> = emptyList(),
                  override val cacheControl: CacheControl? = null,
                  val builder: HTML.() -> Unit) : Resource, StreamConsumer() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val expires = null
    override val contentLength = null
    override val headers by lazy { super.headers }

    override fun stream(out: OutputStream) {
        with(out.bufferedWriter()) {
            append("<!DOCTYPE html>\n")
            appendHTML().html(builder)
            flush()
        }
    }
}

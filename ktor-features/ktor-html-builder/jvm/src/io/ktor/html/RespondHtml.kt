/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.html

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.html.*
import kotlinx.html.stream.*
import java.io.*

/**
 * Responds to a client with a HTML response, using specified [block] to build an HTML page
 */
public suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    respond(HtmlContent(status, block))
}

/**
 * Represents an [OutgoingContent] using `kotlinx.html` builder.
 */
public class HtmlContent(
    override val status: HttpStatusCode? = null,
    private val builder: HTML.() -> Unit
) : OutgoingContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val content = ByteArrayOutputStream()

        try {
            content.bufferedWriter().use {
                it.append("<!DOCTYPE html>\n")
                it.appendHTML().html(block = builder)
            }
        } catch (cause: Throwable) {
            channel.close(cause)
            throw cause
        }

        channel.writeFully(content.toByteArray())
    }
}

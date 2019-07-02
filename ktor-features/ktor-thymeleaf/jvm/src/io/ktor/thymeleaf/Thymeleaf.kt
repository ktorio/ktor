/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.thymeleaf

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.http.withCharset
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import io.ktor.util.cio.bufferedWriter
import io.ktor.utils.io.ByteWriteChannel
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

/**
 * Represents a content handled by [Thymeleaf] feature.
 *
 * @param template name that is resolved by thymeleaf
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
class ThymeleafContent(
    val template: String,
    val model: Map<String, Any>,
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Thymeleaf support feature. Provides ability to respond with [Thymeleaf]
 */
class Thymeleaf(private val engine: TemplateEngine) {
    /**
     * A feature installing companion object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, TemplateEngine, Thymeleaf> {
        override val key = AttributeKey<Thymeleaf>("thymeleaf")

        override fun install(pipeline: ApplicationCallPipeline, configure: TemplateEngine.() -> Unit): Thymeleaf {
            val config = TemplateEngine().apply(configure)
            val feature = Thymeleaf(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is ThymeleafContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    private fun process(content: ThymeleafContent): ThymeleafOutgoingContent {
        return ThymeleafOutgoingContent(
            engine,
            content.template,
            content.model,
            content.etag,
            content.contentType
        )
    }

    private class ThymeleafOutgoingContent(
        val engine: TemplateEngine,
        val template: String,
        val model: Map<String, Any>,
        etag: String?,
        override val contentType: ContentType
    ) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                val context = Context().apply { setVariables(model) }
                engine.process(template, context, it)
            }
        }

        init {
            if (etag != null)
                versions += EntityTagVersion(etag)
        }
    }

}

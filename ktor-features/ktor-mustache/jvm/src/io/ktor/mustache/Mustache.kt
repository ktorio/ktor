/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.mustache

import com.github.mustachejava.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*

/**
 * Response content which could be used to respond [ApplicationCalls] like `call.respond(MustacheContent(...))
 *
 * @param template name of the template to be resolved by Mustache
 * @param model which is passed into the template
 * @param etag value for `E-Tag` header (optional)
 * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
 */
class MustacheContent(
    val template: String,
    val model: Any?,
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Feature for providing Mustache templates as [MustacheContent]
 */
class Mustache(configuration: Configuration) {

    private val mustacheFactory = configuration.mustacheFactory

    class Configuration {
        var mustacheFactory: MustacheFactory = DefaultMustacheFactory()
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Mustache> {
        override val key = AttributeKey<Mustache>("mustache")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Mustache {
            val configuration = Configuration().apply(configure)
            val feature = Mustache(configuration)

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is MustacheContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }

            return feature
        }
    }

    private fun process(content: MustacheContent): MustacheOutgoingContent {
        return MustacheOutgoingContent(
            mustacheFactory.compile(content.template),
            content.model,
            content.etag,
            content.contentType
        )
    }

    /**
     * Content which is responded when Mustache templates are rendered.
     *
     * @param template the compiled [com.github.mustachejava.Mustache] template
     * @param model the model provided into the template
     * @param etag value for `E-Tag` header (optional)
     * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
     */
    private class MustacheOutgoingContent(
        val template: com.github.mustachejava.Mustache,
        val model: Any?,
        etag: String?,
        override val contentType: ContentType
    ) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                template.execute(it, model)
            }
        }

        init {
            if (etag != null)
                versions += EntityTagVersion(etag)
        }
    }
}

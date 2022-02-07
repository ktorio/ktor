/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.mustache

import com.github.mustachejava.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*

/**
 * Response content which could be used to respond [ApplicationCalls] like `call.respond(MustacheContent(...))
 *
 * @param template name of the template to be resolved by Mustache
 * @param model which is passed into the template
 * @param etag value for `E-Tag` header (optional)
 * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
 */
public class MustacheContent(
    public val template: String,
    public val model: Any?,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A plugin that allows you to use Mustache templates as views within your application.
 * Provides the ability to respond with [MustacheContent]
 */
public class Mustache private constructor(configuration: Configuration) {

    private val mustacheFactory = configuration.mustacheFactory

    @KtorDsl
    public class Configuration {
        public var mustacheFactory: MustacheFactory = DefaultMustacheFactory()
    }

    public companion object Plugin : ApplicationPlugin<ApplicationCallPipeline, Configuration, Mustache> {
        override val key: AttributeKey<Mustache> = AttributeKey<Mustache>("mustache")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): Mustache {
            val configuration = Configuration().apply(configure)
            val plugin = Mustache(configuration)

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is MustacheContent) {
                    val response = plugin.process(value)
                    proceedWith(response)
                }
            }

            return plugin
        }
    }

    private fun process(content: MustacheContent): OutgoingContent = with(content) {
        val writer = StringWriter()
        mustacheFactory.compile(content.template).execute(writer, model)

        val result = TextContent(text = writer.toString(), contentType)
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }
}

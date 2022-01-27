/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jte

import gg.jte.*
import gg.jte.output.*
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * Represents content handled by the [Jte] plugin.
 *
 * @param template file name that is resolved by the jte [TemplateEngine]
 * @param params to be passed to the template
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
public class JteContent(
    public val template: String,
    public val params: Map<String, Any?>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A plugin that allows you to use jte templates as views within your application.
 * Provides the ability to respond with [JteContent]
 */
public class Jte private constructor(config: Config) {

    /**
     * The jte [TemplateEngine] used by this plugin
     */
    public val templateEngine: TemplateEngine = config.templateEngine

    public class Config {
        public lateinit var templateEngine: TemplateEngine
    }

    /**
     * A plugin installing companion object
     */
    public companion object Plugin : ApplicationPlugin<ApplicationCallPipeline, Config, Jte> {

        override val key: AttributeKey<Jte> = AttributeKey("jte")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Config.() -> Unit
        ): Jte {
            val plugin = Jte(Config().apply(configure))
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is JteContent)
                    proceedWith(plugin.process(value))
            }
            return plugin
        }
    }

    private fun process(content: JteContent): OutgoingContent {
        val writer = StringOutput()
        templateEngine.render(content.template, content.params, writer)

        val result = TextContent(writer.toString(), content.contentType)
        if (content.etag != null)
            result.versions += EntityTagVersion(content.etag)
        return result
    }
}

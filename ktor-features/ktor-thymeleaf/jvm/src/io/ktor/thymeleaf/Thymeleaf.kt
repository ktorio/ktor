/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.thymeleaf

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import org.thymeleaf.*
import org.thymeleaf.context.*
import java.io.*

/**
 * Represents a content handled by [Thymeleaf] feature.
 *
 * @param template name that is resolved by thymeleaf
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
public class ThymeleafContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Thymeleaf support feature. Provides ability to respond with [Thymeleaf]
 */
public class Thymeleaf(private val engine: TemplateEngine) {
    /**
     * A feature installing companion object
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, TemplateEngine, Thymeleaf> {
        override val key: AttributeKey<Thymeleaf> = AttributeKey<Thymeleaf>("thymeleaf")

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

    private fun process(content: ThymeleafContent): OutgoingContent = with(content) {
        val writer = StringWriter()
        val context = Context().apply { setVariables(model) }
        engine.process(template, context, writer)

        val result = TextContent(text = writer.toString(), contentType)
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }
}

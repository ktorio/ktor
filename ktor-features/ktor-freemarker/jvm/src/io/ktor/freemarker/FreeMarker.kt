/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.freemarker

import freemarker.template.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import java.io.*

/**
 * Represents a content handled by [FreeMarker] feature.
 *
 * @param template name that is resolved by freemarker
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
public class FreeMarkerContent(
    public val template: String,
    public val model: Any?,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Freemarker support feature. Provides ability to respond with [FreeMarkerContent]
 */
public class FreeMarker(private val config: Configuration) {
    /**
     * A feature installing companion object
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, FreeMarker> {
        override val key: AttributeKey<FreeMarker> = AttributeKey("freemarker")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): FreeMarker {
            val config = Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).apply(configure)
            val feature = FreeMarker(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is FreeMarkerContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    private fun process(content: FreeMarkerContent): OutgoingContent = with(content) {
        val writer = StringWriter()
        config.getTemplate(content.template).process(model, writer)

        val result = TextContent(text = writer.toString(), contentType)
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }
}

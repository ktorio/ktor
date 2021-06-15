/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.velocity

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.apache.velocity.*
import org.apache.velocity.app.*
import org.apache.velocity.context.*
import java.io.*

/**
 * Represents a response content that could be used to respond with `call.respond(VelocityContent(...))`
 *
 * @param template name to be resolved by velocity
 * @param model to be passed to the template
 * @param etag header value (optional)
 * @param contentType (optional, `text/html` with UTF-8 character encoding by default)
 */
public class VelocityContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

internal fun velocityOutgoingContent(
    template: Template,
    model: Context,
    etag: String?,
    contentType: ContentType
): OutgoingContent {
    val writer = StringWriter()
    template.merge(model, writer)

    val result = TextContent(text = writer.toString(), contentType)
    if (etag != null) {
        result.versions += EntityTagVersion(etag)
    }
    return result
}

/**
 * Velocity ktor feature. Provides ability to respond with [VelocityContent] and [respondTemplate].
 */
public class Velocity(private val engine: VelocityEngine) {
    init {
        engine.init()
    }

    /**
     * A companion object for installing feature
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, VelocityEngine, Velocity> {
        override val key: AttributeKey<Velocity> = AttributeKey<Velocity>("velocity")

        override fun install(pipeline: ApplicationCallPipeline, configure: VelocityEngine.() -> Unit): Velocity {
            val config = VelocityEngine().apply(configure)
            val feature = Velocity(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is VelocityContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    private fun process(content: VelocityContent): OutgoingContent {
        return velocityOutgoingContent(
            engine.getTemplate(content.template),
            VelocityContext(content.model),
            content.etag,
            content.contentType
        )
    }
}

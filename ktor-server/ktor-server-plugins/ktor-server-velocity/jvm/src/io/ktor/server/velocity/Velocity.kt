/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.velocity

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
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
 * A plugin that allows you to use Velocity templates as views within your application.
 * Provides the ability to respond with [VelocityContent].
 */
public val Velocity: ApplicationPlugin<Application, VelocityEngine, PluginInstance> =
    createApplicationPlugin("Velocity", ::VelocityEngine) {

        pluginConfig.init()

        fun process(content: VelocityContent): OutgoingContent {
            return velocityOutgoingContent(
                pluginConfig.getTemplate(content.template),
                VelocityContext(content.model),
                content.etag,
                content.contentType
            )
        }

        onCallRespond { _, value ->
            if (value is VelocityContent) {
                transformBody {
                    process(value)
                }
            }
        }
    }

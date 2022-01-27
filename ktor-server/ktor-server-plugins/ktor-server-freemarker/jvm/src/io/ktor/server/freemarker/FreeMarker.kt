/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.freemarker

import freemarker.template.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import java.io.*

/**
 * Represents a content handled by [FreeMarker] plugin.
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
 * A plugin that allows you to use FreeMarker templates as views within your application.
 * Provides the ability to respond with [FreeMarkerContent]
 */
public val FreeMarker: ApplicationPlugin<Application, Configuration, PluginInstance> = createApplicationPlugin(
    "FreeMarker",
    { Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS) }
) {
    fun process(content: FreeMarkerContent): OutgoingContent = with(content) {
        val writer = StringWriter()
        pluginConfig.getTemplate(content.template).process(model, writer)

        val result = TextContent(text = writer.toString(), contentType)
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }

    onCallRespond { _, message ->
        if (message is FreeMarkerContent) {
            transformBody {
                process(message)
            }
        }
    }
}

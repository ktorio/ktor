/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.pebble

import com.mitchellbosecke.pebble.*
import com.mitchellbosecke.pebble.template.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*
import java.util.*

/**
 * Response content which could be used to respond [ApplicationCalls] like `call.respond(PebbleContent(...))
 *
 * @param template name of the template to be resolved by Pebble
 * @param model which is passed into the template
 * @param locale which is used to resolve templates (optional)
 * @param etag value for `E-Tag` header (optional)
 * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
 */
public class PebbleContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val locale: Locale? = null,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A plugin that allows you to use Pebble templates as views within your application.
 * Provides the ability to respond with [PebbleContent]
 */
public val Pebble: ApplicationPlugin<Application, PebbleEngine.Builder, PluginInstance> =
    createApplicationPlugin("Pebble", { PebbleEngine.Builder() }) {
        val engine = pluginConfig.build()

        fun process(content: PebbleContent): OutgoingContent = with(content) {
            val writer = StringWriter()
            engine.getTemplate(content.template).evaluate(writer, model, locale)

            val result = TextContent(text = writer.toString(), contentType)
            if (etag != null) {
                result.versions += EntityTagVersion(etag)
            }
            return result
        }

        onCallRespond { _, value ->
            if (value is PebbleContent) {
                transformBody {
                    process(value)
                }
            }
        }
    }

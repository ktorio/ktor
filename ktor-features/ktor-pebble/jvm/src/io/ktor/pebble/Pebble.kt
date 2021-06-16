/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.pebble

import com.mitchellbosecke.pebble.*
import com.mitchellbosecke.pebble.template.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
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
 * Feature for providing Pebble templates as [PebbleContent]
 */
public class Pebble(private val engine: PebbleEngine) {

    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, PebbleEngine.Builder, Pebble> {
        override val key: AttributeKey<Pebble> = AttributeKey<Pebble>("pebble")

        override fun install(pipeline: ApplicationCallPipeline, configure: PebbleEngine.Builder.() -> Unit): Pebble {
            val builder = PebbleEngine.Builder().apply {
                configure(this)
            }
            val feature = Pebble(builder.build())

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is PebbleContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }

            return feature
        }
    }

    private fun process(content: PebbleContent): OutgoingContent = with(content) {
        val writer = StringWriter()
        engine.getTemplate(content.template).evaluate(writer, model, locale)

        val result = TextContent(text = writer.toString(), contentType)
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }
}

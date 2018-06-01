package io.ktor.freemarker

import freemarker.template.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*

class FreeMarkerContent(val template: String,
                        val model: Any?,
                        val etag: String? = null,
                        val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))

class FreeMarker(val config: Configuration) {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, FreeMarker> {
        override val key = AttributeKey<FreeMarker>("freemarker")

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

    private fun process(content: FreeMarkerContent): FreeMarkerOutgoingContent {
        return FreeMarkerOutgoingContent(config.getTemplate(content.template), content.model, content.etag, content.contentType)
    }

    private class FreeMarkerOutgoingContent(val template: Template,
                                            val model: Any?,
                                            etag: String?,
                                            override val contentType: ContentType) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                template.process(model, it)
            }
        }

        init {
            if (etag != null)
                versions += EntityTagVersion(etag)
        }
    }
}

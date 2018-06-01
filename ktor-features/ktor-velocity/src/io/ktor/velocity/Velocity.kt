package io.ktor.velocity

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import org.apache.velocity.*
import org.apache.velocity.app.*

class VelocityContent(val template: String,
                      val model: Map<String, Any>,
                      val etag: String? = null,
                      val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))

class Velocity(private val engine: VelocityEngine) {
    init {
        engine.init()
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, VelocityEngine, Velocity> {
        override val key = AttributeKey<Velocity>("freemarker")

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

    private fun process(content: VelocityContent): VelocityOutgoingContent {
        return VelocityOutgoingContent(engine.getTemplate(content.template), content.model, content.etag, content.contentType)
    }

    private class VelocityOutgoingContent(val template: Template,
                                          val model: Map<String, Any>,
                                          etag: String?,
                                          override val contentType: ContentType) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                template.merge(VelocityContext(model), it)
            }
        }

        init {
            if (etag != null)
                versions += EntityTagVersion(etag)
        }
    }
}

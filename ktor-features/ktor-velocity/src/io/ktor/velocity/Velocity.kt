package io.ktor.velocity

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import org.apache.velocity.*
import org.apache.velocity.app.*

/**
 * Represents a response content that could be used to respond with `call.respond(VelocityContent(...))`
 *
 * @param template name to be resolved by velocity
 * @param model to be passed to the template
 * @param etag header value (optional)
 * @param contentType (optional, `text/html` with UTF-8 character encoding by default)
 */
class VelocityContent(
    val template: String,
    val model: Map<String, Any>,
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Velocity ktor feature. Provides ability to respond with [VelocityContent] and [respondTemplate].
 */
class Velocity(private val engine: VelocityEngine) {
    init {
        engine.init()
    }

    /**
     * A companion object for installing feature
     */
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
        return VelocityOutgoingContent(
            engine.getTemplate(content.template),
            content.model,
            content.etag,
            content.contentType
        )
    }

    private class VelocityOutgoingContent(
        val template: Template,
        val model: Map<String, Any>,
        etag: String?,
        override val contentType: ContentType
    ) : OutgoingContent.WriteChannelContent() {
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

package io.ktor.freemarker

import freemarker.template.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*

/**
 * Represents a content handled by [FreeMarker] feature.
 *
 * @param template name that is resolved by freemarker
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
class FreeMarkerContent(
    val template: String,
    val model: Any?,
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Freemarker support feature. Provides ability to respond with [FreeMarkerContent]
 */
class FreeMarker(private val config: Configuration) {
    /**
     * A feature installing companion object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, FreeMarker> {
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

    private fun process(content: FreeMarkerContent): FreeMarkerOutgoingContent {
        return FreeMarkerOutgoingContent(
            config.getTemplate(content.template),
            content.model,
            content.etag,
            content.contentType
        )
    }

    private class FreeMarkerOutgoingContent(
        val template: Template,
        val model: Any?,
        etag: String?,
        override val contentType: ContentType
    ) : OutgoingContent.WriteChannelContent() {
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

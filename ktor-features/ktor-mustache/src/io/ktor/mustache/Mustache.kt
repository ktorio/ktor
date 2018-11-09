package io.ktor.mustache

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.http.withCharset
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import io.ktor.util.cio.bufferedWriter
import kotlinx.coroutines.io.ByteWriteChannel

/**
 * Response content which could be used to respond [ApplicationCalls] like `call.respond(MustacheContent(...))
 *
 * @param template name of the template to be resolved by Mustache
 * @param model which is passed into the template
 * @param etag value for `E-Tag` header (optional)
 * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
 */
class MustacheContent(
    val template: String,
    val model: Any?,
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Feature for providing Mustache templates as [MustacheContent]
 */
class Mustache(private val mustacheFactory: MustacheFactory) {

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, MustacheFactory, Mustache> {
        override val key = AttributeKey<Mustache>("mustache")

        override fun install(pipeline: ApplicationCallPipeline, configure: MustacheFactory.() -> Unit): Mustache {
            val mustacheFactory = DefaultMustacheFactory().apply(configure)
            val feature = Mustache(mustacheFactory)

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is MustacheContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }

            return feature
        }
    }

    private fun process(content: MustacheContent): MustacheOutgoingContent {
        return MustacheOutgoingContent(
            mustacheFactory.compile(content.template),
            content.model,
            content.etag,
            content.contentType
        )
    }

    /**
     * Content which is responded when Mustache templates are rendered.
     *
     * @param template the compiled [com.github.mustachejava.Mustache] template
     * @param model the model provided into the template
     * @param etag value for `E-Tag` header (optional)
     * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
     */
    private class MustacheOutgoingContent(
        val template: com.github.mustachejava.Mustache,
        val model: Any?,
        etag: String?,
        override val contentType: ContentType
    ) : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                template.execute(it, model)
            }
        }

        init {
            if (etag != null)
                versions += EntityTagVersion(etag)
        }
    }
}

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
 * [HttpClient] feature that encodes [String] request bodies to [TextContent]
 * using a specific charset defined at [HttpPlainText.defaultCharset].
 * And also processes the response body as [String].
 * @param defaultCharset: default charset to use. [Charsets.UTF_8] by default.
 *
 * NOTE: the [HttpPlainText.defaultCharset] is the default one for your JVM so can change between servers!
 *       So please, specify one if you want consistent results in all your deployments.
 */
class HttpPlainText(var defaultCharset: Charset) {

    internal fun read(call: HttpClientCall, body: Input): String {
        val actualCharset = call.response.charset() ?: call.responseConfig.defaultCharset
        return body.readText(charset = actualCharset)
    }

    /**
     * [HttpPlainText] configuration.
     */
    class Config {
        /**
         * Default [Charset] to use.
         */
        var defaultCharset: Charset = Charsets.UTF_8

        internal fun build(): HttpPlainText = HttpPlainText(defaultCharset)
    }

    @Suppress("KDocMissingDocumentation")
    companion object Feature : HttpClientFeature<Config, HttpPlainText> {
        override val key = AttributeKey<HttpPlainText>("HttpPlainText")

        override fun prepare(block: Config.() -> Unit): HttpPlainText = Config().apply(block).build()

        override fun install(feature: HttpPlainText, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { content ->
                if (content !is String) return@intercept
                val contentType = ContentType.Text.Plain.withCharset(feature.defaultCharset)
                proceedWith(TextContent(content, contentType))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
                if (info.type != String::class || body !is ByteReadChannel) return@intercept

                val content = feature.read(context, body.readRemaining())
                proceedWith(HttpResponseContainer(info, content))
            }
        }
    }
}

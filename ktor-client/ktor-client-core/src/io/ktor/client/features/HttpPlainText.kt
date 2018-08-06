package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.io.charsets.*

/**
 * [HttpClient] feature that encodes [String] request bodies to [TextContent]
 * using a specific charset defined at [HttpPlainText.defaultCharset].
 * And also processes the response body as [String].
 *
 * NOTE: the [HttpPlainText.defaultCharset] is the default one for your JVM so can change between servers!
 *       So please, specify one if you want consistent results in all your deployments.
 */
class HttpPlainText(var defaultCharset: Charset) {

    internal suspend fun read(response: HttpResponse): String = response.readText(charset = defaultCharset)

    class Config {
        var defaultCharset: Charset = Charsets.UTF_8

        fun build(): HttpPlainText = HttpPlainText(defaultCharset)
    }

    companion object Feature : HttpClientFeature<Config, HttpPlainText> {
        override val key = AttributeKey<HttpPlainText>("HttpPlainText")

        override fun prepare(block: Config.() -> Unit): HttpPlainText = Config().apply(block).build()

        override fun install(feature: HttpPlainText, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { content ->
                if (content !is String) return@intercept
                val contentType = ContentType.Text.Plain.withCharset(feature.defaultCharset)
                proceedWith(TextContent(content, contentType))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, response) ->
                if (info.type != String::class || response !is HttpResponse) return@intercept

                proceedWith(HttpResponseContainer(info, feature.read(response)))
            }
        }
    }
}

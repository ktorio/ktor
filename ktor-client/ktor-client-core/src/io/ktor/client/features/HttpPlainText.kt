package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.pipeline.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.charset.*


class HttpPlainText(private val defaultCharset: Charset) {
    suspend fun read(response: IncomingContent): String = response.readText()

    class Config {
        var defaultCharset: Charset = Charset.defaultCharset()

        fun build(): HttpPlainText = HttpPlainText(defaultCharset)
    }

    companion object Feature : HttpClientFeature<Config, HttpPlainText> {
        override val key = AttributeKey<HttpPlainText>("HttpPlainText")

        override fun prepare(block: Config.() -> Unit): HttpPlainText = Config().apply(block).build()

        override fun install(feature: HttpPlainText, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { content ->
                if (content !is String) return@intercept
                proceedWith(TextContent(content, ContentType.Text.Plain.withCharset(Charset.defaultCharset())))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (expectedType, response) ->
                if (expectedType != String::class) return@intercept
                if (response !is IncomingContent) return@intercept
                proceedWith(HttpResponseContainer(expectedType, feature.read(response)))
            }
        }
    }
}

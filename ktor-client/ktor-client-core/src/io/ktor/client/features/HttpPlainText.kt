package io.ktor.client.features

import io.ktor.client.*
import io.ktor.pipeline.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.nio.charset.*


class HttpPlainText(val defaultCharset: Charset) {
    suspend fun read(response: HttpResponseBuilder): String? {
        val body = response.body as? HttpMessageBody ?: return null
        val charset = response.charset() ?: defaultCharset

        return when (body) {
            is OutputStreamBody -> {
                return ByteArrayOutputStream().use { stream ->
                    body.block(stream)
                    stream.toString(charset.name())
                }
            }
            is InputStreamBody -> body.stream.use { InputStreamReader(it, charset).readText() }
            is EmptyBody -> ""
        }
    }

    fun write(request: HttpRequestBuilder): HttpMessageBody? {
        val requestString = request.body as? String ?: return null
        val charset = request.charset() ?: defaultCharset
        val body = requestString.toByteArray(charset)

        if (request.headers[HttpHeaders.ContentLength] == null) {
            request.headers[HttpHeaders.ContentLength] = body.size.toString()
        }

        with(request.headers) {
            get(HttpHeaders.ContentType) ?: contentType(ContentType.Text.Plain.withCharset(charset))
        }

        return InputStreamBody(ByteArrayInputStream(body))
    }

    class Config {
        var defaultCharset: Charset = Charset.defaultCharset()

        fun build(): HttpPlainText = HttpPlainText(defaultCharset)
    }

    companion object Feature : HttpClientFeature<Config, HttpPlainText> {
        override val key = AttributeKey<HttpPlainText>("HttpPlainText")

        override fun prepare(block: Config.() -> Unit): HttpPlainText = Config().apply(block).build()

        override fun install(feature: HttpPlainText, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { builder: HttpRequestBuilder ->
                builder.body = feature.write(builder) ?: return@intercept
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (expectedType, _, response) ->
                if (expectedType != String::class) return@intercept

                response.body = feature.read(response) ?: return@intercept
            }
        }
    }
}

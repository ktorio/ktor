package io.ktor.client.features.cookies

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.util.*


class HttpCookies(private val storage: CookiesStorage) {

    operator fun get(host: String): Map<String, Cookie>? = storage[host]

    operator fun get(host: String, name: String): Cookie? = storage[host, name]

    fun forEach(host: String, block: (Cookie) -> Unit) = storage.forEach(host, block)

    class Config {
        private val defaults = mutableListOf<CookiesStorage.() -> Unit>()

        var storage: CookiesStorage = AcceptAllCookiesStorage()

        fun default(block: CookiesStorage.() -> Unit) {
            defaults.add(block)
        }

        fun build(): HttpCookies {
            defaults.forEach { storage.apply(it) }
            return HttpCookies(storage)
        }
    }

    companion object Feature : HttpClientFeature<Config, HttpCookies> {
        override fun prepare(block: Config.() -> Unit): HttpCookies = Config().apply(block).build()

        override val key: AttributeKey<HttpCookies> = AttributeKey("HttpCookies")

        override fun install(feature: HttpCookies, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) { content: OutgoingContent ->
                if (content is OutgoingContent.ProtocolUpgrade) return@intercept

                proceedWith(content.wrapCookies { oldHeaders ->
                    val builder = HeadersBuilder()
                    builder.appendAll(oldHeaders)

                    feature.forEach(context.request.url.host) {
                        builder.append(HttpHeaders.Cookie, renderSetCookieHeader(it))
                    }

                    builder.build()
                })
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.State) {
                val host = context.request.url.host
                context.response.setCookie().forEach {
                    feature.storage[host] = it
                }
            }
        }
    }
}

fun HttpClient.cookies(host: String): Map<String, Cookie> = feature(HttpCookies)?.get(host) ?: mapOf()

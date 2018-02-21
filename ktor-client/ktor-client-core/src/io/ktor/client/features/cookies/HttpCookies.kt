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

    suspend fun get(host: String): Map<String, Cookie>? = storage.get(host)

    suspend fun get(host: String, name: String): Cookie? = storage.get(host, name)

    suspend fun forEach(host: String, block: (Cookie) -> Unit) = storage.forEach(host, block)

    class Config {
        private val defaults = mutableListOf<suspend CookiesStorage.() -> Unit>()

        var storage: CookiesStorage = AcceptAllCookiesStorage()

        fun default(block: suspend CookiesStorage.() -> Unit) {
            defaults.add(block)
        }

        suspend fun build(): HttpCookies {
            defaults.forEach {
                it.invoke(storage)
            }

            return HttpCookies(storage)
        }
    }

    companion object Feature : HttpClientFeature<Config, HttpCookies> {
        override suspend fun prepare(block: Config.() -> Unit): HttpCookies = Config().apply(block).build()

        override val key: AttributeKey<HttpCookies> = AttributeKey("HttpCookies")

        override fun install(feature: HttpCookies, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) { content: OutgoingContent ->
                val cookies = feature.get(context.url.host) ?: return@intercept

                proceedWith(content.wrapHeaders { oldHeaders ->
                    Headers.build {
                        appendAll(oldHeaders)
                        cookies.forEach {
                            append(HttpHeaders.Cookie, renderSetCookieHeader(it.value))
                        }
                    }
                })
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.State) {
                val host = context.request.url.host
                context.response.setCookie().forEach {
                    feature.storage.addCookie(host, it)
                }
            }
        }
    }
}

suspend fun HttpClient.cookies(host: String): Map<String, Cookie> = feature(HttpCookies)?.get(host) ?: mapOf()

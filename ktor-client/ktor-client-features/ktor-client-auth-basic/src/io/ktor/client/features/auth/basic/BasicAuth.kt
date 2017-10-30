package io.ktor.client.features.auth.basic

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.util.*

class BasicAuth(val username: String, val password: String) {

    class Configuration {
        lateinit var username: String
        lateinit var password: String

        fun build(): BasicAuth = BasicAuth(username, password)
    }

    companion object Feature : HttpClientFeature<Configuration, BasicAuth> {

        override val key: AttributeKey<BasicAuth> = AttributeKey("AuthBasicHeader")

        override fun prepare(block: Configuration.() -> Unit): BasicAuth = Configuration().apply(block).build()

        override fun install(feature: BasicAuth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) { request: HttpRequestBuilder ->
                if (request.headers.getAll(HttpHeaders.Authorization) != null) return@intercept

                request.headers.append(HttpHeaders.Authorization, constructBasicAuthValue(feature.username, feature.password))
            }
        }

        fun constructBasicAuthValue(username: String, password: String): String {
            val authString = "$username:$password"
            val authBuf = encodeBase64(authString.toByteArray(Charsets.ISO_8859_1))

            return "Basic $authBuf"
        }
    }
}

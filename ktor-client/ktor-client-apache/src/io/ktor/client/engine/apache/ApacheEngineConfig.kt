package io.ktor.client.engine.apache

import io.ktor.client.*
import org.apache.http.client.config.*
import org.apache.http.impl.nio.client.*

class ApacheEngineConfig : HttpClientEngineConfig() {
    var followRedirects: Boolean = false
    var socketTimeout = 10_000
    var connectTimeout = 10_000
    var connectionRequestTimeout = 20_000

    var customRequest: (RequestConfig.Builder.() -> RequestConfig.Builder) = { this }
        private set

    var customClient: (HttpAsyncClientBuilder.() -> HttpAsyncClientBuilder) = { this }
        private set

    fun customizeRequest(block: RequestConfig.Builder.() -> Unit) {
        val current = customRequest
        customRequest = { current(); block(); this }
    }

    fun customizeClient(block: HttpAsyncClientBuilder.() -> Unit) {
        val current = customClient
        customClient = { current(); block(); this }
    }
}
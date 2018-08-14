package io.ktor.client.engine.okhttp

import io.ktor.client.engine.*
import okhttp3.*

class OkHttpConfig : HttpClientEngineConfig() {
    internal var config: OkHttpClient.Builder.() -> Unit = {}

    fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }

    }

    fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
}
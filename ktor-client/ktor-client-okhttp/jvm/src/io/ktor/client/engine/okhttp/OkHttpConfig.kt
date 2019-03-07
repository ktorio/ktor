package io.ktor.client.engine.okhttp

import io.ktor.client.engine.*
import okhttp3.*
import java.util.concurrent.*

/**
 * Configuration for [OkHttp] client engine.
 */
class OkHttpConfig : HttpClientEngineConfig() {

    internal var config: OkHttpClient.Builder.() -> Unit = {}

    /**
     * Preconfigured [OkHttpClient] instance instead of configuring one.
     */
    var preconfigured: OkHttpClient? = null

    /**
     * Configure [OkHttpClient] using [OkHttpClient.Builder].
     */
    fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }

    }

    /**
     * Add [Interceptor] to [OkHttp] client.
     */
    fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    /**
     * Add network [Interceptor] to [OkHttp] client.
     */
    fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
}

package io.ktor.client.engine.okhttp

import io.ktor.client.engine.*
import okhttp3.*
import java.util.concurrent.*

class OkHttpConfig : HttpClientEngineConfig() {
    internal var config: OkHttpClient.Builder.() -> Unit = {
        readTimeout(60, TimeUnit.SECONDS)
        writeTimeout(60, TimeUnit.SECONDS)
    }

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

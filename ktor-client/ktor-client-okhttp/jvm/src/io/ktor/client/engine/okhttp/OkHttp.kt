package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.engine.*

object OkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
        OkHttpEngine(OkHttpConfig().apply(block))
}

class OkHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = OkHttp
}

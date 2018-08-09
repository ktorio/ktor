package io.ktor.client.engine.js

import io.ktor.client.engine.*

object Js : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
        JsClientEngine(HttpClientEngineConfig().apply(block))
}

@JsName("JsClient")
fun JsClient(): HttpClientEngineFactory<HttpClientEngineConfig> = Js
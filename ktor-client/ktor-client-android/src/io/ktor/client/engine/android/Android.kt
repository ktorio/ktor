package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.engine.*

object Android : HttpClientEngineFactory<AndroidEngineConfig> {
    override fun create(block: AndroidEngineConfig.() -> Unit): HttpClientEngine =
        AndroidClientEngine(AndroidEngineConfig().apply(block))
}

class AndroidEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Android
}

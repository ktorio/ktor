package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using a [UrlConnection] based backend implementation without additional dependencies
 * with the the associated configuration [AndroidEngineConfig].
 */
object Android : HttpClientEngineFactory<AndroidEngineConfig> {
    override fun create(block: AndroidEngineConfig.() -> Unit): HttpClientEngine =
        AndroidClientEngine(AndroidEngineConfig().apply(block))
}

@Suppress("KDocMissingDocumentation")
class AndroidEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Android
}

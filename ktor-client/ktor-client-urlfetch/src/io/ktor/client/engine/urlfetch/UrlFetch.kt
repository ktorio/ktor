package io.ktor.client.engine.urlfetch

import io.ktor.client.*
import io.ktor.client.engine.*

object UrlFetch : HttpClientEngineFactory<UrlFetchConfig> {
    override fun create(block: UrlFetchConfig.() -> Unit): HttpClientEngine =
        UrlFetchEngine(UrlFetchConfig().apply(block))
}

class UrlFetchEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = UrlFetch
}

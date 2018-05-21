package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using a Coroutine based I/O implementation without additional dependencies
 * with the the associated configuration [HttpClientEngineConfig].
 *
 * Just supports HTTP/1.x requests.
 */
object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
        CIOEngine(CIOEngineConfig().apply(block))
}

class CIOEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = CIO
}

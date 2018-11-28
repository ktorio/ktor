package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.util.*

/**
 * [HttpClientEngineFactory] using a Coroutine based I/O implementation without additional dependencies
 * with the the associated configuration [CIOEngineConfig].
 *
 * Just supports HTTP/1.x and HTTPS requests.
 */
@KtorExperimentalAPI
object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
        CIOEngine(CIOEngineConfig().apply(block))
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
class CIOEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = CIO
}

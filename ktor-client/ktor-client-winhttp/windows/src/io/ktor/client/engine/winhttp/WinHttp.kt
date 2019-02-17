package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private val initHook = WinHttp

/**
 * [HttpClientEngineFactory] using a curl library in implementation
 * with the the associated configuration [HttpClientEngineConfig].
 */
object WinHttp : HttpClientEngineFactory<HttpClientEngineConfig> {

    init {
        engines.add(this)
    }

    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return WinHttpClientEngine(WinHttpClientEngineConfig().apply(block))
    }
}

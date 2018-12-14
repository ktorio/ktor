package io.ktor.client.engine.ios

import io.ktor.client.engine.*

@ThreadLocal
private val initHook = Ios

object Ios : HttpClientEngineFactory<IosClientEngineConfig> {

    init {
        engines.add(this)
    }

    override fun create(block: IosClientEngineConfig.() -> Unit): HttpClientEngine =
        IosClientEngine(IosClientEngineConfig().apply(block))
}

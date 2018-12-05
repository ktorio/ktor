package io.ktor.client.engine.ios

import io.ktor.client.engine.*

object Ios : HttpClientEngineFactory<IosClientEngineConfig> {
    override fun create(block: IosClientEngineConfig.() -> Unit): HttpClientEngine =
        IosClientEngine(IosClientEngineConfig().apply(block))
}

fun IosClient(): HttpClientEngineFactory<IosClientEngineConfig> = Ios

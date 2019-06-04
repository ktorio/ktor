/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using a [OkHttp] based backend implementation
 * with the the associated configuration [OkHttpConfig].
 */
object OkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
        OkHttpEngine(OkHttpConfig().apply(block))
}

@Suppress("KDocMissingDocumentation")
class OkHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = OkHttp

    override fun toString(): String = "OkHttp"
}

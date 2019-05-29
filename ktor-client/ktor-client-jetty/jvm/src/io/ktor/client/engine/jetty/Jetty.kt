/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.util.*

/**
 * [HttpClientEngineFactory] using `org.eclipse.jetty.http2:http2-client`
 * with the the associated configuration [JettyEngineConfig].
 *
 * Just supports HTTP/2 requests.
 */
object Jetty : HttpClientEngineFactory<JettyEngineConfig> {
    override fun create(block: JettyEngineConfig.() -> Unit): HttpClientEngine =
        JettyHttp2Engine(JettyEngineConfig().apply(block))
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
class JettyEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Jetty

    override fun toString(): String = "Jetty"
}

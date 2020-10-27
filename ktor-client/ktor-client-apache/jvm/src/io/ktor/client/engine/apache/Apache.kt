/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using `org.apache.httpcomponents.httpasyncclient`
 * with the the associated configuration [ApacheEngineConfig].
 *
 * Supports HTTP/2 and HTTP/1.x requests.
 */
public object Apache : HttpClientEngineFactory<ApacheEngineConfig> {
    override fun create(block: ApacheEngineConfig.() -> Unit): HttpClientEngine {
        val config = ApacheEngineConfig().apply(block)
        return ApacheEngine(config)
    }
}

@Suppress("KDocMissingDocumentation")
public class ApacheEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Apache

    override fun toString(): String = "Apache"
}

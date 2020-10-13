/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using a [UrlConnection] based backend implementation without additional dependencies
 * with the the associated configuration [AndroidEngineConfig].
 */
public object Android : HttpClientEngineFactory<AndroidEngineConfig> {
    override fun create(block: AndroidEngineConfig.() -> Unit): HttpClientEngine =
        AndroidClientEngine(AndroidEngineConfig().apply(block))
}

@Suppress("KDocMissingDocumentation")
public class AndroidEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Android

    override fun toString(): String = "Android"
}

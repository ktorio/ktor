/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.client.features.*
import io.ktor.util.*
import kotlin.native.concurrent.*

/**
 * Key required to access capabilities.
 */
@SharedImmutable
internal val ENGINE_CAPABILITIES_KEY =
    AttributeKey<MutableMap<HttpClientEngineCapability<*>, Any>>("EngineCapabilities")

/**
 * Default capabilities expected to be supported by engine.
 */
@SharedImmutable
public val DEFAULT_CAPABILITIES: Set<HttpTimeout.Feature> = setOf(HttpTimeout)

/**
 * Capability required by request to be supported by [HttpClientEngine] with [T] representing type of the capability
 * configuration.
 */
public interface HttpClientEngineCapability<T>

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.reflect.*

/**
 * This plugin provides ability to invoke [ApplicationCall.receive] several times.
 *
 * Please note that all content will be stored into memory by default, it can cause high memory consumption.
 * If you want to limit it, consider using [DoubleReceiveConfig.maxSize] method.
 *
 */
public val DoubleReceive: RouteScopedPlugin<DoubleReceiveConfig, PluginInstance> = createRouteScopedPlugin(
    "DoubleReceive",
    { DoubleReceiveConfig() }
) {
    val filters = pluginConfig.filters
    val cacheRawRequest: Boolean = pluginConfig.cacheRawRequest

    on(ReceiveBytes) { call, state: ApplicationReceiveRequest ->
        if (filters.any { it(call, state) }) return@on state.value

        val cache = call.receiveCache

        if (cache.containsKey(state.typeInfo.type)) {
            return@on cache[state.typeInfo.type]!!
        }

        if (!cacheRawRequest) return@on state.value

        val cacheValue = cache[ByteArray::class] as? ByteArray
        if (cacheValue != null) {
            return@on ByteReadChannel(cacheValue)
        }

        val value = state.value as? ByteReadChannel ?: return@on state
        val content = value.readRemaining().readBytes()
        cache[ByteArray::class] = content
        return@on ByteReadChannel(content)
    }

    on(ReceiveBodyTransformed) { call, state ->
        if (filters.any { it(call, state) }) return@on state.value

        val cache = call.receiveCache
        cache[state.value::class] = state.value
        return@on state.value
    }
}

private val ApplicationCall.receiveCache: ReceiveCache get() =
    attributes.computeIfAbsent(ReceiveCacheKey) { mutableMapOf() }

private val ReceiveCacheKey = AttributeKey<ReceiveCache>("ReceiveCache")

private typealias ReceiveCache = MutableMap<KClass<*>, Any>

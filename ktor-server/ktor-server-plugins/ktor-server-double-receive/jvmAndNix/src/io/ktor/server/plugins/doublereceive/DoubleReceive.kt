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

    on(ReceiveBytes) { call, body ->
        if (filters.any { it(call, body) }) return@on body

        val cache = call.receiveCache

        if (cache.containsKey(call.receiveType.type)) {
            return@on cache[call.receiveType.type]!!
        }

        if (!cacheRawRequest) return@on body

        val cacheValue = cache[ByteArray::class] as? ByteArray
        if (cacheValue != null) {
            return@on ByteReadChannel(cacheValue)
        }

        val value = body as? ByteReadChannel ?: return@on body
        val content = value.readRemaining().readBytes()
        cache[ByteArray::class] = content
        return@on ByteReadChannel(content)
    }

    on(ReceiveBodyTransformed) { call, body ->
        if (filters.any { it(call, body) }) return@on body

        val cache = call.receiveCache
        cache[body::class] = body
        return@on body
    }
}

private val ApplicationCall.receiveCache: ReceiveCache get() =
    attributes.computeIfAbsent(ReceiveCacheKey) { mutableMapOf() }

private val ReceiveCacheKey = AttributeKey<ReceiveCache>("ReceiveCache")

private typealias ReceiveCache = MutableMap<KClass<*>, Any>

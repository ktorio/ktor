/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import java.util.*

/**
 * Configuration for DefaultHeaders plugin. Configure other headers
 * in addition to the Date and Server provided by DefaultHeaders.
 */
public class DefaultHeadersConfig {
    /**
     * Provides a builder to append any custom headers to be sent with each request
     */
    internal val headers = HeadersBuilder()

    /**
     * Adds standard header property [name] with the specified [value].
     */
    public fun header(name: String, value: String): Unit = headers.append(name, value)

    /**
     * Provides time source. Useful for testing.
     */
    public var clock: () -> Long = { System.currentTimeMillis() }
    internal val cachedDateText: AtomicRef<String> = atomic("")
}

/**
 * Adds the standard `Date` and `Server` HTTP headers and provides the ability
 * to add additional default headers into each response.
 */
public val DefaultHeaders: RouteScopedPlugin<DefaultHeadersConfig, PluginInstance> = createRouteScopedPlugin(
    "DefaultHeaders",
    createConfiguration = {
        DefaultHeadersConfig()
    }
) {
    val ktorPackageVersion = if (pluginConfig.headers.getAll(HttpHeaders.Server) == null) {
        pluginConfig::class.java.`package`.implementationVersion ?: "debug"
    } else "debug"
    val headers = pluginConfig.headers.build()
    val DATE_CACHE_TIMEOUT_MILLISECONDS = 1000
    val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")!!
    var cachedDateTimeStamp: Long = 0L
    val calendar = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar {
            return Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)
        }
    }

    fun now(time: Long): GMTDate {
        return calendar.get().toDate(time)
    }

    fun calculateDateHeader(): String {
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = pluginConfig.clock()
        if (captureCached + DATE_CACHE_TIMEOUT_MILLISECONDS <= currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            pluginConfig.cachedDateText.value = now(currentTimeStamp).toHttpDate()
        }
        return pluginConfig.cachedDateText.value
    }

    onCallRespond { call, _ ->
        call.response.header(HttpHeaders.Date, calculateDateHeader())
        headers.forEach { name, value -> value.forEach { call.response.header(name, it) } }
        call.response.headers.append(HttpHeaders.Server, "Ktor/$ktorPackageVersion")
    }
}

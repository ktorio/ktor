/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import java.util.*

/**
 * Adds standard HTTP headers `Date` and `Server` and provides ability to specify other headers
 * that are included in responses.
 */
public class DefaultHeaders(config: Configuration) {
    private val headers = config.headers.build()
    @OptIn(InternalAPI::class)
    private val clock = config.clock

    private var cachedDateTimeStamp: Long = 0L
    private val cachedDateText = atomic("")

    /**
     * Configuration for [DefaultHeaders] plugin.
     */
    public class Configuration {
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
    }

    private fun intercept(call: ApplicationCall) {
        appendDateHeader(call)
        headers.forEach { name, value -> value.forEach { call.response.header(name, it) } }
    }

    private fun appendDateHeader(call: ApplicationCall) {
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = clock()
        if (captureCached + DATE_CACHE_TIMEOUT_MILLISECONDS <= currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            cachedDateText.value = now(currentTimeStamp).toHttpDate()
        }
        call.response.header(HttpHeaders.Date, cachedDateText.value)
    }

    private fun now(time: Long): GMTDate {
        return calendar.get().toDate(time)
    }

    /**
     * Installable plugin for [DefaultHeaders].
     */
    public companion object Plugin : ApplicationPlugin<Application, Configuration, DefaultHeaders> {
        private const val DATE_CACHE_TIMEOUT_MILLISECONDS = 1000

        private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")!!

        private val calendar = object : ThreadLocal<Calendar>() {
            override fun initialValue(): Calendar {
                return Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)
            }
        }

        override val key: AttributeKey<DefaultHeaders> = AttributeKey("Default Headers")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DefaultHeaders {
            val config = Configuration().apply(configure)
            if (config.headers.getAll(HttpHeaders.Server) == null) {
                val ktorPackageName: String = DefaultHeaders::class.java.`package`.implementationTitle ?: "Ktor"
                val ktorPackageVersion: String = DefaultHeaders::class.java.`package`.implementationVersion ?: "debug"

                config.headers.append(HttpHeaders.Server, "$ktorPackageName/$ktorPackageVersion")
            }

            val plugin = DefaultHeaders(config)
            pipeline.intercept(ApplicationCallPipeline.Plugins) { plugin.intercept(call) }
            return plugin
        }
    }
}

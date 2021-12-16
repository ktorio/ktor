/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.seconds

/**
 * Adds the standard `Date` and `Server` HTTP headers and provides the ability
 * to add additional default headers into each response.
 */
public class DefaultHeaders private constructor(config: Configuration) {
    private val headers = config.headers.build()

    private var cachedDateTimeStamp = Instant.DISTANT_PAST
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
    }

    private fun intercept(call: ApplicationCall, now: Instant) {
        appendDateHeader(call, now)
        headers.forEach { name, value -> value.forEach { call.response.header(name, it) } }
    }

    private fun appendDateHeader(call: ApplicationCall, now: Instant) {
        val captureCached = cachedDateTimeStamp
        if (captureCached + DATE_CACHE_TIMEOUT <= now) {
            cachedDateTimeStamp = now
            cachedDateText.value = now.toHttpDate()
        }
        call.response.header(HttpHeaders.Date, cachedDateText.value)
    }

    /**
     * An installable plugin for [DefaultHeaders].
     */
    public companion object Plugin : RouteScopedPlugin<Configuration, DefaultHeaders> {
        private val DATE_CACHE_TIMEOUT = 1.seconds

        override val key: AttributeKey<DefaultHeaders> = AttributeKey("Default Headers")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): DefaultHeaders {
            val config = Configuration().apply(configure)
            if (config.headers.getAll(HttpHeaders.Server) == null) {
                val ktorPackageVersion: String = DefaultHeaders::class.java.`package`.implementationVersion ?: "debug"

                config.headers.append(HttpHeaders.Server, "Ktor/$ktorPackageVersion")
            }

            val plugin = DefaultHeaders(config)
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                plugin.intercept(call, call.application.environment.clock.now())
            }
            return plugin
        }
    }
}

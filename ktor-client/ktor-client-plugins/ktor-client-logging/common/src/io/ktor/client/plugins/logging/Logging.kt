/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*

/**
 * A client's logging plugin.
 */
public class Logging private constructor(
    public val logger: Logger,
    public var level: LogLevel,
    public var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList(),
    public var strategy: Strategy,
    public var logWriter: LogWriter,
) {

    private val mutex = Mutex()

    /**
     * [Logging] plugin configuration
     */
    @KtorDsl
    public class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

        /**
         * [Logger] instance to use
         */
        public var logger: Logger = Logger.DEFAULT

        /**
         * log [LogLevel]
         */
        public var level: LogLevel = LogLevel.HEADERS

        /**
         * Log messages for calls matching a [predicate]
         */
        public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }

        public var strategy: Strategy = Strategy.DEFAULT
    }

    private suspend fun beginLogging() {
        mutex.lock()
    }

    private fun doneLogging() {
        mutex.unlock()
    }

    public companion object : HttpClientPlugin<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(
                config.logger,
                config.level,
                config.filters,
                config.strategy,
                SimpleLogWriter(config.logger, config.level)
            )
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = if (plugin.filters.isEmpty() || plugin.filters.any { it(context) }) {
                    try {
                        plugin.beginLogging()
                        plugin.strategy.log(context, plugin.logWriter)
                    } catch (_: Throwable) {
                        null
                    } finally {
                        plugin.doneLogging()
                    }
                } else null

                try {
                    proceedWith(response ?: subject)
                } catch (cause: Throwable) {
                    plugin.logWriter.write(context, cause)
                    throw cause
                } finally {
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                try {
                    plugin.beginLogging()
                    plugin.strategy.log(response, plugin.logWriter)
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    plugin.logWriter.write(response, cause)
                    throw cause
                } finally {
                    if (!plugin.level.body) {
                        plugin.doneLogging()
                    }
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    proceed()
                } catch (cause: Throwable) {
                    plugin.logWriter.write(context.response, cause)
                    throw cause
                }
            }

            if (!plugin.level.body) {
                return
            }

            val observer: ResponseHandler = {
                try {
                    plugin.strategy.logResponseBody(it, plugin.logWriter)
                } catch (_: Throwable) {
                } finally {
                    plugin.doneLogging()
                }
            }

            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
public fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}

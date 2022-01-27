/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.concurrent.*
import kotlin.collections.set

/**
 * Mutable configuration used by [HttpClient].
 */
@HttpClientDsl
public class HttpClientConfig<T : HttpClientEngineConfig> {
    private val plugins: MutableMap<AttributeKey<*>, (HttpClient) -> Unit> = mutableMapOf()
    private val pluginConfigurations: MutableMap<AttributeKey<*>, Any.() -> Unit> = mutableMapOf()
    private val customInterceptors: MutableMap<String, (HttpClient) -> Unit> = mutableMapOf()

    internal var engineConfig: T.() -> Unit = {}

    /**
     * Configure engine parameters.
     */
    public fun engine(block: T.() -> Unit) {
        val oldConfig = engineConfig
        engineConfig = {
            oldConfig()
            block()
        }
    }

    /**
     * Use [HttpRedirect] plugin to automatically follow redirects.
     */
    public var followRedirects: Boolean = true

    /**
     * Use [defaultTransformers] to automatically handle simple [ContentType].
     */
    public var useDefaultTransformers: Boolean = true

    /**
     * Terminate [HttpClient.receivePipeline] if status code is not successful (>=300).
     */
    public var expectSuccess: Boolean = false

    /**
     * Indicate if client should use development mode. In development mode client pipelines have advanced stack traces.
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Installs a specific [plugin] and optionally [configure] it.
     */
    public fun <TBuilder : Any, TPlugin : Any> install(
        plugin: HttpClientPlugin<TBuilder, TPlugin>,
        configure: TBuilder.() -> Unit = {}
    ) {
        val previousConfigBlock = pluginConfigurations[plugin.key]
        pluginConfigurations[plugin.key] = {
            previousConfigBlock?.invoke(this)

            @Suppress("UNCHECKED_CAST")
            (this as TBuilder).configure()
        }

        if (plugins.containsKey(plugin.key)) return

        plugins[plugin.key] = { scope ->
            val attributes = scope.attributes.computeIfAbsent(PLUGIN_INSTALLED_LIST) { Attributes(concurrent = true) }
            val config = scope.config.pluginConfigurations[plugin.key]!!
            val pluginData = plugin.prepare(config)

            plugin.install(pluginData, scope)
            attributes.put(plugin.key, pluginData)
        }
    }

    /**
     * Installs an interceptor defined by [block].
     * The [key] parameter is used as a unique name, that also prevents installing duplicated interceptors.
     */
    public fun install(key: String, block: HttpClient.() -> Unit) {
        customInterceptors[key] = block
    }

    /**
     * Applies all the installed [plugins] and [customInterceptors] from this configuration
     * into the specified [client].
     */
    public fun install(client: HttpClient) {
        plugins.values.forEach { client.apply(it) }
        customInterceptors.values.forEach { client.apply(it) }
    }

    /**
     * Clones this [HttpClientConfig] duplicating all the [plugins] and [customInterceptors].
     */
    public fun clone(): HttpClientConfig<T> {
        val result = HttpClientConfig<T>()
        result += this
        return result
    }

    /**
     * Install plugin from [other] client config.
     */
    public operator fun plusAssign(other: HttpClientConfig<out T>) {
        followRedirects = other.followRedirects
        useDefaultTransformers = other.useDefaultTransformers
        expectSuccess = other.expectSuccess

        plugins += other.plugins
        pluginConfigurations += other.pluginConfigurations
        customInterceptors += other.customInterceptors
    }
}

/**
 * Dsl marker for [HttpClient] dsl.
 */
@DslMarker
public annotation class HttpClientDsl

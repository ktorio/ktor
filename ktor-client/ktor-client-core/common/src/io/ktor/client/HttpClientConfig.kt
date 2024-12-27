/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * A mutable [HttpClient] configuration used to adjust settings, install plugins and interceptors.
 *
 * This configuration can be provided as a lambda in the [HttpClient] constructor or the [HttpClient.config] builder:
 * ```kotlin
 * val client = HttpClient { // HttpClientConfig<Engine>()
 *     // Configure engine settings
 *     engine { // HttpClientEngineConfig
 *         threadsCount = 4
 *         pipelining = true
 *     }
 *
 *     // Install and configure plugins
 *     install(ContentNegotiation) {
 *         json()
 *     }
 *
 *     // Configure default request parameters
 *     defaultRequest {
 *         url("https://api.example.com")
 *         header("X-Custom-Header", "value")
 *     }
 *
 *     // Configure client-wide settings
 *     expectSuccess = true
 *     followRedirects = true
 * }
 * ```
 * ## Configuring [HttpClientEngine]
 *
 * If the engine is specified explicitly, engine-specific properties will be available in the `engine` block:
 * ```kotlin
 * val client = HttpClient(CIO) { // HttpClientConfig<CIOEngineConfig>.() -> Unit
 *     engine { // CIOEngineConfig.() -> Unit
 *         // engine specific properties
 *     }
 * }
 * ```
 *
 * Learn more about the client's configuration from
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 */
@KtorDsl
public class HttpClientConfig<T : HttpClientEngineConfig> {
    private val plugins: MutableMap<AttributeKey<*>, (HttpClient) -> Unit> = mutableMapOf()
    private val pluginConfigurations: MutableMap<AttributeKey<*>, Any.() -> Unit> = mutableMapOf()
    private val customInterceptors: MutableMap<String, (HttpClient) -> Unit> = mutableMapOf()

    internal var engineConfig: T.() -> Unit = {}

    /**
     * A builder for configuring engine-specific settings in [HttpClientEngineConfig],
     * such as dispatcher, thread count, proxy, and more.
     *
     * ```kotlin
     * val client = HttpClient(CIO) { // HttpClientConfig<CIOEngineConfig>
     *     engine { // CIOEngineConfig.() -> Unit
     *         proxy = ProxyBuilder.http("proxy.example.com", 8080)
     *     }
     * ```
     *
     * You can learn more from [Engines](https://ktor.io/docs/http-client-engines.html).
     */
    public fun engine(block: T.() -> Unit) {
        val oldConfig = engineConfig
        engineConfig = {
            oldConfig()
            block()
        }
    }

    /**
     * Specifies whether the client redirects to URLs provided in the `Location` header.
     * You can disable redirections by setting this property to `false`.
     *
     * For an advanced redirection configuration, use the [HttpRedirect] plugin.
     */
    public var followRedirects: Boolean = true

    /**
     * Enables body transformations for many common types like [String], [ByteArray], [ByteReadChannel], etc.
     * These transformations are applied to the request and response bodies.
     *
     * The transformers will be used when the response body is received with a type:
     * ```kotlin
     * val client = HttpClient()
     * val bytes = client.get("https://ktor.io")
     *                   .body<ByteArray>()
     * ```
     *
     * This flag is enabled by default.
     * You might want to disable it if you want to write your own transformers or handle body manually.
     *
     * For more details, see the [defaultTransformers] documentation.
     */
    public var useDefaultTransformers: Boolean = true

    /**
     * Terminates [HttpClient.receivePipeline] if the status code is not successful (>=300).
     * Learn more from [Response validation](https://ktor.io/docs/response-validation.html).
     *
     * For more details, see the [HttpCallValidator] documentation.
     */
    public var expectSuccess: Boolean = false

    /**
     * Development mode is no longer required all functionality is enabled by default. The property is safe to remove.
     */
    @Deprecated(
        "Development mode is no longer required. The property will be removed in the future.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("")
    )
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Installs the specified [plugin] and optionally configures it using the [configure] block.
     *
     * ```kotlin
     * val client = HttpClient {
     *     install(ContentNegotiation) {
     *         // configuration block
     *         json()
     *     }
     * }
     * ```
     *
     * If the plugin is already installed, the configuration block will be applied to the existing configuration class.
     *
     * Learn more from [Plugins](https://ktor.io/docs/http-client-plugins.html).
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
     *
     * If the [key] is already used, the new interceptor will replace the old one.
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
     * Clones this [HttpClientConfig] by duplicating all the [plugins] and [customInterceptors].
     */
    public fun clone(): HttpClientConfig<T> {
        val result = HttpClientConfig<T>()
        result += this
        return result
    }

    /**
     * Installs the plugin from the [other] client's configuration.
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

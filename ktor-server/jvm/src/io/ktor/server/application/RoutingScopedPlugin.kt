/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Gets plugin instance for this pipeline, or fails with [MissingApplicationPluginException] if the plugin is not installed
 * @throws MissingApplicationPluginException
 * @param plugin application plugin to lookup
 * @return an instance of plugin
 */
public fun <A : ApplicationCallPipeline, B : Any, F : Any> A.plugin(plugin: RoutingScopedPlugin<A, B, F>): F {
    return findPluginInRoute(plugin) ?: throw MissingApplicationPluginException(plugin.key)
}

/**
 * Defines a Plugin that can be installed into [Routing]
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfiguration is the type for the configuration object for this Plugin
 * @param TPlugin is the type for the instance of the Plugin object
 */
public interface RoutingScopedPlugin<
    in TPipeline : ApplicationCallPipeline, TConfiguration : Any, TPlugin : Any> :
    Plugin<TPipeline, TConfiguration, TPlugin> {

    /**
     * Unique key that identifies a plugin configuration
     */
    public val configKey: AttributeKey<TConfiguration>
        get() = EquatableAttributeKey("${key.name}_config")

    /**
     * Provider for configuration instance
     */
    public fun createConfiguration(): TConfiguration

    /**
     * Plugin installation script
     */
    public fun install(pipeline: TPipeline): TPlugin

    /**
     * Instance of [TConfiguration], configured for this pipeline
     */
    public val PipelineContext<*, ApplicationCall>.config: (TConfiguration)
        get() = call.attributes[configKey]
}

/**
 * Installs [plugin] into this pipeline, if it is not yet installed
 */
public fun <P : ApplicationCallPipeline, B : Any, F : Any> P.install(
    plugin: RoutingScopedPlugin<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(plugin.configKey, plugin.createConfiguration().apply(configure))
    }

    val installedPlugin = findPluginInRoute(plugin)
    if (installedPlugin != null) {
        return installedPlugin
    }

    // routing scoped plugin needs to be installed into routing. Otherwise, configuration block will not be overwritten
    @Suppress("UNCHECKED_CAST")
    val installPipeline = when (this) {
        is Application -> routing {} as P
        else -> this
    }
    val installed = plugin.install(installPipeline)
    val registry = installPipeline.pluginRegistry
    registry.put(plugin.key, installed)
    return installed
}

@Suppress("UNCHECKED_CAST")
private fun <P : ApplicationCallPipeline, B : Any, F : Any> P.findPluginInRoute(
    plugin: RoutingScopedPlugin<P, B, F>
): F? {
    var current: Route? = this as? Route
    while (current != null) {
        val registry = current.pluginRegistry
        val installedFeature = registry.getOrNull(plugin.key)
        if (installedFeature != null) {
            return installedFeature
        }
        current = current.parent
    }
    return null
}

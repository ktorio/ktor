/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Defines a Plugin that can be installed into [Routing]
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfiguration is the type for the configuration object for this Plugin
 * @param TPlugin is the type for the instance of the Plugin object
 */
public interface SubroutePlugin<TConfiguration : Any, TPlugin : Any> :
    Plugin<Route, TConfiguration, TPlugin> {

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
    public fun install(pipeline: Route): TPlugin

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
    plugin: SubroutePlugin<B, F>,
    configure: B.() -> Unit = {}
): F {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(plugin.configKey, plugin.createConfiguration().apply(configure))
    }

    // routing scoped plugin needs to be installed into routing. Otherwise, configuration block will not be overwritten
    @Suppress("UNCHECKED_CAST")
    val installPipeline = when (this) {
        is Application -> routing {}
        is Route -> this
        else -> throw IllegalStateException("RoutingScopedPlugin can be installed only in Application or Route")
    }

    val installedPlugin = installPipeline.findPluginInRoute(plugin)
    if (installedPlugin != null) {
        return installedPlugin
    }
    val installed = plugin.install(installPipeline)
    val registry = installPipeline.pluginRegistry
    registry.put(plugin.key, installed)
    return installed
}

@Suppress("UNCHECKED_CAST")
public fun <F : Any> Route.findPluginInRoute(plugin: Plugin<*, *, F>): F? {
    var current = this
    while (true) {
        val installedFeature = current.pluginOrNull(plugin)
        if (installedFeature != null) {
            return installedFeature
        }
        if (current.parent == null) {
            break
        }
        current = current.parent!!
    }
    if (current is Routing) {
        return application.pluginOrNull(plugin)
    }
    return null
}

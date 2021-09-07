/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

/**
 * A factory class to be passed to the [install] function for creating a [ServerPlugin]
 * instance and installing it into the current application context.
 **/
public sealed class ServerPluginFactory<Configuration : Any, TPlugin : ServerPlugin<Configuration>> :
    Plugin<ApplicationCallPipeline, Configuration, TPlugin>

/**
 * A factory class to be passed to the [install] function for creating a [ServerPlugin.ApplicationPlugin]
 * instance and installing it into the current application context.
 **/
public abstract class ServerApplicationPluginFactory<Configuration : Any> :
    ServerPluginFactory<Configuration, ServerPlugin.ApplicationPlugin<Configuration>>(),
    ApplicationPlugin<ApplicationCallPipeline, Configuration, ServerPlugin.ApplicationPlugin<Configuration>>

/**
 * A factory class to be passed to the [install] function for creating a [ServerPlugin.RoutingScopedPlugin]
 * instance and installing it into the current application context.
 **/
public abstract class ServerRoutingScopedPluginFactory<Configuration : Any> :
    ServerPluginFactory<Configuration, ServerPlugin.RoutingScopedPlugin<Configuration>>(),
    RoutingScopedPlugin<ApplicationCallPipeline, Configuration, ServerPlugin.RoutingScopedPlugin<Configuration>>

/**
 * Gets a plugin instance for this pipeline, or fails with [MissingApplicationPluginException]
 * if the plugin is not installed.
 * @throws MissingApplicationPluginException
 * @param plugin plugin to lookup
 * @return an instance of plugin
 */
public fun <A : Pipeline<*, ApplicationCall>, ConfigurationT : Any> A.plugin(
    plugin: ServerPluginFactory<ConfigurationT, *>
): ServerPlugin<*> {
    return pluginRegistry.getOrNull(plugin.key)
        ?: throw MissingApplicationPluginException(plugin.key)
}

internal typealias PipelineHandler = (Pipeline<*, ApplicationCall>) -> Unit

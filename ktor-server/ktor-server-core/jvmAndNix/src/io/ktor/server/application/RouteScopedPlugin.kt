/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.application.plugins.api.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Defines a Plugin that can be installed into [Route]
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfig is the configuration object type for this Plugin
 * @param TPlugin is the instance type of the Plugin object
 */
public interface RouteScopedPlugin<TConfiguration : Any, TPlugin : Any> :
    Plugin<ApplicationCallPipeline, TConfiguration, TPlugin>

/**
 * Installs [plugin] into this pipeline, if it is not yet installed
 */
public fun <P : ApplicationCallPipeline, B : Any, F : Any> P.install(
    plugin: RouteScopedPlugin<B, F>,
    configure: B.() -> Unit = {}
): F {
    if (pluginRegistry.getOrNull(plugin.key) != null) {
        throw DuplicatePluginException(
            "Plugin `${plugin.key.name}` is already installed to the pipeline $this"
        )
    }
    if (this is Route && application.pluginRegistry.getOrNull(plugin.key) != null) {
        throw DuplicatePluginException(
            "Installing RouteScopedPlugin to application and route is not supported. " +
                "Consider moving application level install to routing root."
        )
    }
    // we install plugin into fake pipeline and add interceptors manually
    // to avoid having multiple interceptors after pipelines are merged
    val fakePipeline = when (this is Route) {
        true -> Route(parent, selector, developmentMode, environment)
        else -> ApplicationCallPipeline(developmentMode, environment)
    }
    val installed = plugin.install(fakePipeline, configure)
    pluginRegistry.put(plugin.key, installed)

    mergePhases(fakePipeline)
    receivePipeline.mergePhases(fakePipeline.receivePipeline)
    sendPipeline.mergePhases(fakePipeline.sendPipeline)

    val installPipeline = this
    addAllInterceptors(fakePipeline, installPipeline, plugin, installed)
    receivePipeline.addAllInterceptors(fakePipeline.receivePipeline, installPipeline, plugin, installed)
    sendPipeline.addAllInterceptors(fakePipeline.sendPipeline, installPipeline, plugin, installed)

    return installed
}

private fun <B : Any, F : Any, TSubject, TContext, P : Pipeline<TSubject, TContext>> P.addAllInterceptors(
    fakePipeline: P,
    installPipeline: ApplicationCallPipeline,
    plugin: RouteScopedPlugin<B, F>,
    pluginInstance: F
) {
    items.forEach { phase ->
        fakePipeline.interceptorsForPhase(phase)
            .forEach { interceptor ->
                intercept(phase) { subject ->
                    val call = context
                    if (call is RoutingApplicationCall && call.route.findPluginInRoute(plugin) == pluginInstance) {
                        interceptor(this, subject)
                        return@intercept
                    }
                    if (installPipeline is Application) {
                        interceptor(this, subject)
                    }
                }
            }
    }
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

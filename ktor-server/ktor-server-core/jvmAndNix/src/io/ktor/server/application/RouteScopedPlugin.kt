/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Defines a Plugin that can be installed into [Route]
 * @param TConfig is the configuration object type for this Plugin
 * @param TPlugin is the instance type of the Plugin object
 */
public interface RouteScopedPlugin<TConfiguration : Any, TPlugin : Any> :
    Plugin<ApplicationCallPipeline, TConfiguration, TPlugin>

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

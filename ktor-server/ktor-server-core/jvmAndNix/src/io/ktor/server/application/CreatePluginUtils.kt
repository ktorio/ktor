/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Creates an [ApplicationPlugin] that can be installed into [Application].
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [Application].
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created. Please
 * note that it may be modified later when a user of your plugin calls [Application.install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createApplicationPlugin("MyPlugin") {
 *      // This block will be executed when you call install(MyPlugin)
 *
 *      onCall { call ->
 *          // Prints requested URL each time your application receives a call:
 *          println(call.request.uri)
 *      }
 * }
 *
 * application.install(MyPlugin)
 * ```
 **/
public fun <PluginConfigT : Any> createApplicationPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: ApplicationPluginBuilder<PluginConfigT>.() -> Unit
): ApplicationPlugin<Application, PluginConfigT, PluginInstance> =
    object : ApplicationPlugin<Application, PluginConfigT, PluginInstance> {
        override val key: AttributeKey<PluginInstance> = AttributeKey(name)

        override fun install(
            pipeline: Application,
            configure: PluginConfigT.() -> Unit
        ): PluginInstance {
            return createPluginInstance(pipeline, pipeline, body, createConfiguration, configure)
        }
    }

/**
 * Creates a [RouteScopedPlugin] that can be installed into [io.ktor.server.routing.Route].
 *
 * @param name A name of your new plugin that is used if you need find an instance of
 * your plugin when it is installed to an [io.ktor.server.routing.Routing].
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created. Please
 * note that it may be modified later when a user of your plugin calls [install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createRouteScopedPlugin("MyPlugin") {
 *      // This block will be executed when you call install(MyPlugin)
 *
 *      onCall { call ->
 *          // Prints requested URL each time your application receives a call:
 *          println(call.request.uri)
 *      }
 * }
 *
 * route("a") {
 *   install(MyPlugin)
 * }
 * ```
 **/
public fun <PluginConfigT : Any> createRouteScopedPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: ApplicationPluginBuilder<PluginConfigT>.() -> Unit
): RouteScopedPlugin<PluginConfigT, PluginInstance> = object : RouteScopedPlugin<PluginConfigT, PluginInstance> {

    override val key: AttributeKey<PluginInstance> = AttributeKey(name)

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: PluginConfigT.() -> Unit
    ): PluginInstance {
        val application = when (pipeline) {
            is Route -> pipeline.application
            is Application -> pipeline
            else -> error("Unsupported pipeline type: ${pipeline::class}")
        }

        return createPluginInstance(application, pipeline, body, createConfiguration, configure)
    }
}

/**
 * Creates a [ApplicationPlugin] that can be installed into [Application].
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [Application].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createApplicationPlugin("MyPlugin") {
 *      // This block will be executed when you call install(MyPlugin)
 *
 *      onCall { call ->
 *          // Prints requested URL each time your application receives a call:
 *          println(call.request.uri)
 *      }
 * }
 *
 * application.install(MyPlugin)
 * ```
 **/
public fun createApplicationPlugin(
    name: String,
    body: ApplicationPluginBuilder<Unit>.() -> Unit
): ApplicationPlugin<Application, Unit, PluginInstance> =
    createApplicationPlugin(name, {}, body)

/**
 * Creates a [RouteScopedPlugin] that can be installed into [Application].
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [Application].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createRouteScopedPlugin("MyPlugin") {
 *      // This block will be executed when you call install(MyPlugin)
 *
 *      onCall { call ->
 *          // Prints requested URL each time your application receives a call:
 *          println(call.request.uri)
 *      }
 * }
 *
 * route("a") {
 *   install(MyPlugin)
 * }
 * ```
 **/
public fun createRouteScopedPlugin(
    name: String,
    body: ApplicationPluginBuilder<Unit>.() -> Unit
): RouteScopedPlugin<Unit, PluginInstance> = createRouteScopedPlugin(name, {}, body)

private fun <
    PipelineT : ApplicationCallPipeline,
    PluginConfigT : Any
    > Plugin<PipelineT, PluginConfigT, PluginInstance>.createPluginInstance(
    application: Application,
    pipeline: ApplicationCallPipeline,
    body: ApplicationPluginBuilder<PluginConfigT>.() -> Unit,
    createConfiguration: () -> PluginConfigT,
    configure: PluginConfigT.() -> Unit
): PluginInstance {
    val config = createConfiguration().apply(configure)

    val currentPlugin = this
    val pluginBuilder = object : ApplicationPluginBuilder<PluginConfigT>(currentPlugin.key) {
        override val application: Application = application
        override val pipeline: ApplicationCallPipeline = pipeline
        override val pluginConfig: PluginConfigT = config
    }

    pluginBuilder.setupPlugin(body)
    return PluginInstance(pluginBuilder)
}

private fun <Configuration : Any, Plugin : ApplicationPluginBuilder<Configuration>> Plugin.setupPlugin(
    body: Plugin.() -> Unit
) {
    apply(body)

    callInterceptions.forEach {
        it.action(pipeline)
    }

    onReceiveInterceptions.forEach {
        it.action(pipeline.receivePipeline)
    }

    onResponseInterceptions.forEach {
        it.action(pipeline.sendPipeline)
    }

    afterResponseInterceptions.forEach {
        it.action(pipeline.sendPipeline)
    }

    hooks.forEach { it.install(pipeline) }
}

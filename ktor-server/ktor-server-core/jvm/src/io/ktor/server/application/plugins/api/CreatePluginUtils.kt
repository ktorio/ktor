/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*

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
 * val MyPlugin = createPlugin("MyPlugin") {
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
    body: PluginBuilder.ApplicationPluginBuilder<PluginConfigT>.() -> Unit
): ApplicationPlugin<Application, PluginConfigT, PluginInstance> =
    object : ApplicationPlugin<Application, PluginConfigT, PluginInstance> {
        override val key: AttributeKey<PluginInstance> = AttributeKey(name)

        override fun install(
            pipeline: Application,
            configure: PluginConfigT.() -> Unit
        ): PluginInstance {
            val config = createConfiguration()
            config.configure()

            val currentPlugin = this
            val pluginBuilder = object : PluginBuilder.ApplicationPluginBuilder<PluginConfigT>(currentPlugin) {
                override val pipeline: ApplicationCallPipeline = pipeline
                override val pluginConfig: PluginConfigT = config
            }
            pluginBuilder.setupPlugin(body)
            return PluginInstance(pluginBuilder)
        }
    }

/**
 * Creates a [SubroutePlugin] that can be installed into [io.ktor.server.routing.Routing].
 *
 * @param name A name of your new plugin that will be used if you need find an instance of
 * your plugin when it is installed to an [io.ktor.server.routing.Routing].
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created. Please
 * note that it may be modified later when a user of your plugin calls [install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createPlugin("MyPlugin") {
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
public fun <PluginConfigT : Any> createSubroutePlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: PluginBuilder.SubroutePluginBuilder<PluginConfigT>.() -> Unit
): SubroutePlugin<PluginConfigT, PluginInstance> =
    object : SubroutePlugin<PluginConfigT, PluginInstance> {

        override val key: AttributeKey<PluginInstance> = AttributeKey(name)

        override fun createConfiguration(): PluginConfigT =
            createConfiguration.invoke()

        override fun install(
            pipeline: Route
        ): PluginInstance {
            val currentPlugin = this
            val pluginBuilder = object : PluginBuilder.SubroutePluginBuilder<PluginConfigT>(currentPlugin) {
                override val pipeline: ApplicationCallPipeline = pipeline
            }
            pluginBuilder.setupPlugin(body)
            return PluginInstance(pluginBuilder)
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
 * val MyPlugin = createPlugin("MyPlugin") {
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
    body: PluginBuilder<Unit>.() -> Unit
): ApplicationPlugin<Application, Unit, PluginInstance> =
    createApplicationPlugin(name, {}, body)

/**
 * Creates a [SubroutePlugin] that can be installed into [Application].
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [Application].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 * Usage example:
 * ```
 * val MyPlugin = createPlugin("MyPlugin") {
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
public fun createSubroutePlugin(
    name: String,
    body: PluginBuilder.SubroutePluginBuilder<Unit>.() -> Unit
): SubroutePlugin<Unit, PluginInstance> = createSubroutePlugin(name, {}, body)

private fun <Configuration : Any, Plugin : PluginBuilder<Configuration>> Plugin.setupPlugin(
    body: Plugin.() -> Unit
) {
    apply(body)

    pipelineHandlers.forEach { handle ->
        handle(pipeline)
    }

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
}

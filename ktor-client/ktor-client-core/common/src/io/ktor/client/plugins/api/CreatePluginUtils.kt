/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.util.*

public interface ClientPlugin<PluginConfig : Any> : HttpClientPlugin<PluginConfig, ClientPluginInstance<PluginConfig>>

public fun <PluginConfigT : Any> createClientPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: ClientPluginBuilder<PluginConfigT>.() -> Unit
): ClientPlugin<PluginConfigT> =
    object : ClientPlugin<PluginConfigT> {
        override val key: AttributeKey<ClientPluginInstance<PluginConfigT>> = AttributeKey(name)

        override fun prepare(block: PluginConfigT.() -> Unit): ClientPluginInstance<PluginConfigT> {
            val config = createConfiguration().apply(block)
            return ClientPluginInstance(config)
        }

        override fun install(plugin: ClientPluginInstance<PluginConfigT>, scope: HttpClient) {
            val pluginBuilder = object : ClientPluginBuilder<PluginConfigT>(AttributeKey(name)) {
                override val client: HttpClient = scope
                override val pluginConfig: PluginConfigT = plugin.config
            }.apply(body)
            pluginBuilder.hooks.forEach { it.install(scope) }
        }
    }

public fun createClientPlugin(
    name: String,
    body: ClientPluginBuilder<Unit>.() -> Unit
): ClientPlugin<Unit> = createClientPlugin(name, {}, body)

private fun <Configuration : Any, Plugin : ClientPluginBuilder<Configuration>> Plugin.setupPlugin(
    body: Plugin.() -> Unit
) {
    apply(body)
}

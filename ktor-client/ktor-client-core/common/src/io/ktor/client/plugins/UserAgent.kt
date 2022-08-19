/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

internal val UserAgentPlugin = createClientPlugin("UserAgent", UserAgent::Config) {

    val agent = pluginConfig.agent

    onRequest { request, _ ->
        request.header(HttpHeaders.UserAgent, agent)
    }
}

/**
 * A plugin that adds a `User-Agent` header to all requests.
 *
 * @property agent a `User-Agent` header value.
 */
public class UserAgent private constructor(
    @get:Deprecated("This will be removed in future versions") public val agent: String
) {

    @KtorDsl
    public class Config(public var agent: String = "Ktor http-client")

    public companion object Plugin : HttpClientPlugin<Config, ClientPluginInstance<Config>> {
        override val key: AttributeKey<ClientPluginInstance<Config>> = AttributeKey("UserAgent")

        override fun prepare(block: Config.() -> Unit): ClientPluginInstance<Config> {
            return UserAgentPlugin.prepare(block)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ClientPluginInstance<Config>, scope: HttpClient) {
            plugin.install(scope)
        }
    }
}

/**
 * Installs the [UserAgent] plugin with a browser-like user agent.
 */
public fun HttpClientConfig<*>.BrowserUserAgent() {
    install(UserAgent) {
        agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Ubuntu Chromium/70.0.3538.77 Chrome/70.0.3538.77 Safari/537.36"
    }
}

/**
 * Installs the [UserAgent] plugin with a CURL user agent.
 */
public fun HttpClientConfig<*>.CurlUserAgent() {
    install(UserAgent) {
        agent = "curl/7.61.0"
    }
}

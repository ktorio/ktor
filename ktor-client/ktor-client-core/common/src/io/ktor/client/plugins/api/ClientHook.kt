/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.utils.*
import io.ktor.utils.io.*

/**
 * A hook that can be registered in [ClientPluginBuilder].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.api.ClientHook)
 */
@KtorDsl
public interface ClientHook<HookHandler> {

    /**
     * Specifies how to install a hook into [client].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.api.ClientHook.install)
     */
    public fun install(client: HttpClient, handler: HookHandler)
}

internal class HookHandler<T>(
    private val pluginName: String,
    private val hook: ClientHook<T>,
    private val handler: T
) {
    fun install(client: HttpClient) {
        val hookName = when (val name = hook::class.simpleName) {
            "RequestHook" -> "onRequest"
            "ResponseHook" -> "onResponse"
            "TransformRequestBodyHook" -> "transformRequestBody"
            "TransformResponseBodyHook" -> "transformResponseBody"
            else -> name ?: "hook"
        }
        client.withInterceptorTraceName(pluginName, hookName) {
            hook.install(client, handler)
        }
    }
}

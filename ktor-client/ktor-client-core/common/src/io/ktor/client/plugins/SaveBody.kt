/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// Preserve the old class name for binary compatibility
@file:JvmName("DoubleReceivePluginKt")

package io.ktor.client.plugins

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmName

private val SKIP_SAVE_BODY = AttributeKey<Unit>("SkipSaveBody")
private val RESPONSE_BODY_SAVED = AttributeKey<Unit>("ResponseBodySaved")

private val LOGGER by lazy { KtorSimpleLogger("io.ktor.client.plugins.SaveBody") }

/**
 * [SaveBody] saves response body in memory, so it can be read multiple times and resources can be freed up immediately.
 *
 * @see HttpClientCall.save
 */
@OptIn(InternalAPI::class)
internal val SaveBody: ClientPlugin<Unit> = createClientPlugin("SaveBody") {
    client.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
        val call = response.call
        val attributes = call.attributes
        if (attributes.contains(SKIP_SAVE_BODY)) return@intercept

        val savedResponse = try {
            call.save().response
        } finally {
            runCatching { response.rawContent.cancel() }
                .onFailure { LOGGER.debug("Failed to cancel response body", it) }
        }

        attributes.put(RESPONSE_BODY_SAVED, Unit)
        proceedWith(savedResponse)
    }
}

internal fun HttpRequestBuilder.skipSaveBody() {
    attributes.put(SKIP_SAVE_BODY, Unit)
}

/**
 * Configuration for [SaveBodyPlugin]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.SaveBodyPluginConfig)
 */
public class SaveBodyPluginConfig {
    /**
     * Disables the plugin for all request.
     *
     * If you need to disable it only for the specific request, please use [HttpRequestBuilder.skipSavingBody]:
     * ```kotlin
     * client.get("http://myurl.com") {
     *     skipSavingBody()
     * }
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.SaveBodyPluginConfig.disabled)
     */
    public var disabled: Boolean = false
}

/**
 * [SaveBodyPlugin] saving the whole body in memory, so it can be received multiple times.
 *
 * It may be useful to prevent saving body in case of big size or streaming. To do so use [HttpRequestBuilder.skipSavingBody]:
 * ```kotlin
 * client.get("http://myurl.com") {
 *     skipSavingBody()
 * }
 * ```
 *
 * The plugin is installed by default, if you need to disable it use:
 * ```kotlin
 * val client = HttpClient {
 *     install(SaveBodyPlugin) {
 *         disabled = true
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.SaveBodyPlugin)
 */
@Deprecated("This plugin is not needed anymore.\n$API_REMOVAL_MESSAGE")
public val SaveBodyPlugin: ClientPlugin<SaveBodyPluginConfig> = createClientPlugin(
    "DoubleReceivePlugin",
    ::SaveBodyPluginConfig
) {
    if (pluginConfig.disabled) {
        LOGGER.warn(
            "It is not possible to disable body saving for all requests anymore. " +
                "To disable it for a specific response, handle it as a streaming response.\n" +
                SHARE_USE_CASE_MESSAGE
        )
    } else {
        LOGGER.warn(
            "SaveBodyPlugin plugin is deprecated and can be safely removed. " +
                "Request body is saved in memory by default for all non-streaming responses."
        )
    }
}

/**
 * Returns `true` if response body is saved and can be read multiple times.
 * By default, all non-streaming responses are saved.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.isSaved)
 */
public val HttpResponse.isSaved: Boolean
    get() = call.attributes.contains(RESPONSE_BODY_SAVED)

/**
 * Prevent saving response body in memory for the specific request.
 *
 * To disable the plugin for all requests use [SaveBodyPluginConfig.disabled] property:
 * ```kotlin
 * val client = HttpClient {
 *     install(SaveBodyPlugin) {
 *         disabled = true
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.skipSavingBody)
 */
@Deprecated(
    "Skipping of body saving for a specific request is not allowed anymore.\n" +
        "$SHARE_USE_CASE_MESSAGE\n" +
        API_REMOVAL_MESSAGE
)
@Suppress("UnusedReceiverParameter")
public fun HttpRequestBuilder.skipSavingBody() {
    LOGGER.warn("Skipping of body saving for a specific request is not allowed anymore. $SHARE_USE_CASE_MESSAGE")
}

private const val SHARE_USE_CASE_MESSAGE =
    "If you were relying on this functionality, share your use case in comments to this issue: " +
        "https://youtrack.jetbrains.com/issue/KTOR-8367/"
private const val API_REMOVAL_MESSAGE = "This API is deprecated and will be removed in Ktor 4.0.0"

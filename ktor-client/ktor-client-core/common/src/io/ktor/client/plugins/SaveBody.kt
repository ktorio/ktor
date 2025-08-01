/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// Preserve the old class name for binary compatibility
@file:JvmName("DoubleReceivePluginKt")

package io.ktor.client.plugins

import io.ktor.client.call.*
import io.ktor.client.plugins.Messages.PLUGIN_DEPRECATED_MESSAGE
import io.ktor.client.plugins.Messages.SAVE_BODY_DISABLED_MESSAGE
import io.ktor.client.plugins.Messages.SAVE_BODY_ENABLED_MESSAGE
import io.ktor.client.plugins.Messages.SKIP_SAVING_BODY_MESSAGE
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
        if (attributes.contains(SKIP_SAVE_BODY)) {
            LOGGER.trace { "Skipping body saving for ${call.request.url}" }
            return@intercept
        }

        val savedResponse = try {
            LOGGER.trace { "Saving body for ${call.request.url}" }
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
@Deprecated(PLUGIN_DEPRECATED_MESSAGE)
public class SaveBodyPluginConfig {
    /**
     * Disables the plugin for all requests.
     * Note that the body of streaming responses is not saved.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.SaveBodyPluginConfig.disabled)
     */
    @Deprecated(SAVE_BODY_DISABLED_MESSAGE)
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
@Suppress("DEPRECATION")
@Deprecated(PLUGIN_DEPRECATED_MESSAGE)
public val SaveBodyPlugin: ClientPlugin<SaveBodyPluginConfig> = createClientPlugin(
    "DoubleReceivePlugin",
    ::SaveBodyPluginConfig
) {
    if (pluginConfig.disabled) {
        LOGGER.warn(SAVE_BODY_DISABLED_MESSAGE)
    } else {
        LOGGER.warn(SAVE_BODY_ENABLED_MESSAGE)
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
@Deprecated(SKIP_SAVING_BODY_MESSAGE)
@Suppress("UnusedReceiverParameter")
public fun HttpRequestBuilder.skipSavingBody() {
    LOGGER.warn(SKIP_SAVING_BODY_MESSAGE)
}

private object Messages {
    private const val USE_STREAMING_SYNTAX =
        "Use client.prepareRequest(...).execute { ... } syntax to prevent saving the body in memory."
    private const val API_WILL_BE_REMOVED =
        "This API is deprecated and will be removed in Ktor 4.0.0"
    private const val SHARE_USE_CASE =
        "If you were relying on this functionality, share your use case by commenting on this issue: " +
            "https://youtrack.jetbrains.com/issue/KTOR-8367/"

    const val SAVE_BODY_ENABLED_MESSAGE =
        "The SaveBodyPlugin plugin is deprecated and can be safely removed. " +
            "Request bodies are now saved in memory by default for all non-streaming responses."

    const val SAVE_BODY_DISABLED_MESSAGE =
        "It is no longer possible to disable body saving for all requests. " +
            USE_STREAMING_SYNTAX + "\n\n" +
            API_WILL_BE_REMOVED + "\n" +
            SHARE_USE_CASE

    const val PLUGIN_DEPRECATED_MESSAGE =
        "This plugin is no longer needed.\n" +
            API_WILL_BE_REMOVED

    const val SKIP_SAVING_BODY_MESSAGE =
        "Skipping of body saving for a specific request is no longer allowed.\n" +
            USE_STREAMING_SYNTAX + "\n\n" +
            API_WILL_BE_REMOVED + "\n" +
            SHARE_USE_CASE
}

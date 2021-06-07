/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

import io.ktor.util.*

/**
 * WebSocket extensions API is experimental according to [KTOR-688](https://youtrack.jetbrains.com/issue/KTOR-688)
 * To get more information about Ktor experimental guarantees consult
 * with [KTOR-1035](https://youtrack.jetbrains.com/issue/KTOR-1035).
 */
@RequiresOptIn(
    message = "WebSocket extensions API is experimental according to https://youtrack.jetbrains.com/issue/KTOR-688." +
        "To get more information about Ktor experimental guarantees consult " +
        "with https://youtrack.jetbrains.com/issue/KTOR-1035",
    level = RequiresOptIn.Level.ERROR
)
public annotation class ExperimentalWebSocketExtensionApi

@OptIn(ExperimentalWebSocketExtensionApi::class)
private typealias ExtensionInstaller = () -> WebSocketExtension<*>

/**
 * Factory that defines WebSocket extension. The factory is used in pair with [WebSocketExtensionsConfig.install]
 * method to install WebSocket extension in client or server.
 *
 * Usually this interface implemented in `companion object` of the origin [WebSocketExtension].
 */
@ExperimentalWebSocketExtensionApi
public interface WebSocketExtensionFactory<ConfigType : Any, ExtensionType : WebSocketExtension<ConfigType>> {
    /**
     * Key is used to locate extension.
     */
    public val key: AttributeKey<ExtensionType>

    /**
     * First extension bit used by current extension.
     *
     * This flag is used to detect extension conflicts: only one feature with enabled flag is allowed.
     * To set the flag value please consult with specification of the extension you're using.
     */
    public val rsv1: Boolean

    /**
     * Second extension bit used by current extension.
     *
     * This flag is used to detect extension conflicts: only one feature with enabled flag is allowed.
     * To set the flag value please consult with specification of the extension you're using.
     */
    public val rsv2: Boolean

    /**
     * Third extension bit used by current extension.
     *
     * This flag is used to detect extension conflicts: only one feature with enabled flag is allowed.
     * To set the flag value please consult with specification of the extension you're using.
     */
    public val rsv3: Boolean

    /**
     * Create extension instance using [config] block. The extension instance is created for each WebSocket request.
     */
    public fun install(config: ConfigType.() -> Unit): ExtensionType
}

/**
 * WebSocket extension instance. This instance is created for each WebSocket request, for every installed extension by
 * [WebSocketExtensionFactory].
 */
@ExperimentalWebSocketExtensionApi
public interface WebSocketExtension<ConfigType : Any> {

    /**
     * Reference to the [WebSocketExtensionFactory], which produced this extension.
     */
    public val factory: WebSocketExtensionFactory<ConfigType, out WebSocketExtension<ConfigType>>

    /**
     * List of WebSocket extension protocols which will be sent by client in headers.
     * They are required to inform server that client wants to negotiate current extension.
     */
    public val protocols: List<WebSocketExtensionHeader>

    /**
     * This method is called only for a client, when it receives the WebSocket upgrade response.
     *
     * @param negotiatedProtocols contains list of negotiated extensions from server (can be empty).
     *
     * It's up to extension to decide if it should be used or not.
     * @return `true` if the extension should be used by the client.
     */
    public fun clientNegotiation(negotiatedProtocols: List<WebSocketExtensionHeader>): Boolean

    /**
     * This method is called only for a server, when it receives websocket session.
     *
     * @param requestedProtocols contains list of requested extensions from client (can be empty).
     *
     * @return list of protocols (with parameters) which server prefer to use for current client request.
     */
    public fun serverNegotiation(
        requestedProtocols: List<WebSocketExtensionHeader>
    ): List<WebSocketExtensionHeader>

    /**
     * This method is called on each outgoing frame and handle it before sending.
     */
    public fun processOutgoingFrame(frame: Frame): Frame

    /**
     * This method is called on each incoming frame before handling it in WebSocket session.
     */
    public fun processIncomingFrame(frame: Frame): Frame
}

/**
 * Extensions configuration for WebSocket client and server features.
 */
@ExperimentalWebSocketExtensionApi
public class WebSocketExtensionsConfig {
    private val installers: MutableList<ExtensionInstaller> = mutableListOf()
    private val rcv: Array<Boolean> = arrayOf(false, false, false)

    /**
     * Install provided [extension] using [config]. Every extension is processed in order of installation.
     */
    public fun <ConfigType : Any> install(
        extension: WebSocketExtensionFactory<ConfigType, *>,
        config: ConfigType.() -> Unit = {}
    ) {
        checkConflicts(extension)
        installers += { extension.install(config) }
    }

    /**
     * Instantiate all installed extensions.
     */
    public fun build(): List<WebSocketExtension<*>> = installers.map { it() }

    private fun checkConflicts(extensionFactory: WebSocketExtensionFactory<*, *>) {
        var hasConflict = extensionFactory.rsv1 && rcv[1]
        hasConflict = hasConflict || extensionFactory.rsv2 && rcv[2]
        hasConflict = hasConflict || extensionFactory.rsv3 && rcv[3]

        check(!hasConflict) { "Failed to install extension. Please check configured extensions for conflicts." }
    }
}

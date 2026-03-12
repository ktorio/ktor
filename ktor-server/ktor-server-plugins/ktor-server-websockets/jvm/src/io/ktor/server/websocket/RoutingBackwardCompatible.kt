/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("RoutingKt")

package io.ktor.server.websocket

import io.ktor.server.routing.*
import io.ktor.websocket.*

// Deprecated stubs preserving binary compatibility after return type changed from Unit to Route.
// Kept in JVM sources only because klib IR signatures do not differentiate by return type.

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, handler)
}

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, negotiateExtensions, handler)
}

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocketRaw(protocol: String? = null, handler: suspend WebSocketServerSession.() -> Unit) {
    webSocketRaw(protocol, handler)
}

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    webSocketRaw(protocol, negotiateExtensions, handler)
}

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocket(
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocket(protocol, handler)
}

@Deprecated("Use the variant that returns Route.", level = DeprecationLevel.HIDDEN)
public fun Route.webSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocket(path, protocol, handler)
}

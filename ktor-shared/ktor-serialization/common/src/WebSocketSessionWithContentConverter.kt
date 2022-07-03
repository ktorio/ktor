/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.websocket.*

public interface WebSocketSessionWithContentConverter : WebSocketSession {
    /**
     * Converter for web socket session, if plugin [WebSockets] is installed
     */
    public val converter: WebsocketContentConverter?
}

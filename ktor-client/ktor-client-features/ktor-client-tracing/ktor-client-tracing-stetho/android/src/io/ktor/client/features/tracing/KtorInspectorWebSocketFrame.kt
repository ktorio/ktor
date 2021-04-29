/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import com.facebook.stetho.inspector.network.*
import io.ktor.http.cio.websocket.*

internal class KtorInspectorWebSocketFrame(
    private val requestId: String,
    private val frame: Frame
) : NetworkEventReporter.InspectorWebSocketFrame {

    override fun requestId(): String {
        return requestId
    }

    override fun mask(): Boolean {
        return false
    }

    override fun opcode(): Int {
        return frame.frameType.opcode
    }

    override fun payloadData(): String {
        return String(frame.data)
    }

}

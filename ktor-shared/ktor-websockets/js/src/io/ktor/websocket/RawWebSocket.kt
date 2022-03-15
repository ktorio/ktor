/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.utils.io.*
import kotlin.coroutines.*

@Suppress("FunctionName")
public actual fun RawWebSocket(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    maxFrameSize: Long,
    masking: Boolean,
    coroutineContext: CoroutineContext
): WebSocketSession = RawWebSocketCommon(input, output, maxFrameSize, masking, coroutineContext)

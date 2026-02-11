/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.internal.*
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Raised when the frame is bigger than allowed in a current WebSocket session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.FrameTooBigException)
 *
 * @param frameSize size of received or posted frame that is too big
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class FrameTooBigException(
    public val frameSize: Long,
    cause: Throwable? = null,
) : Exception(cause), CopyableThrowable<FrameTooBigException> {

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(frameSize: Long) : this(frameSize, cause = null)

    override val message: String
        get() = "Frame is too big: $frameSize"

    override fun createCopy(): FrameTooBigException = FrameTooBigException(frameSize).also {
        it.initCauseBridge(this)
    }
}

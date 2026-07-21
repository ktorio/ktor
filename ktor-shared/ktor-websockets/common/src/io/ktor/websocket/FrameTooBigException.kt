/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

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
        get() {
            val sizeSuffix = if (frameSize >= 0) ": $frameSize" else ""
            return "Frame is too big$sizeSuffix"
        }

    override fun createCopy(): FrameTooBigException = FrameTooBigException(frameSize, this)
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

/**
 * Wraps this channel to execute the provided action when closed using `flushAndClose()`.
 *
 * @param onClose The action to execute when the channel is closed.
 * @return A new `ByteWriteChannel` that executes the given action upon closure.
 */
public fun ByteWriteChannel.onClose(onClose: suspend () -> Unit): ByteWriteChannel =
    CloseHookByteWriteChannel(this, onClose)

internal class CloseHookByteWriteChannel(
    private val delegate: ByteWriteChannel,
    private val onClose: suspend () -> Unit
) : ByteWriteChannel by delegate {
    override suspend fun flushAndClose() {
        delegate.flushAndClose()
        onClose()
    }
}

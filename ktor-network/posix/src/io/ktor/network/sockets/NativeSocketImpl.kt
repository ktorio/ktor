/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.CoroutineContext

internal abstract class NativeSocketImpl(
    private val selector: SelectorManager,
    override val descriptor: Int,
    parent: CoroutineContext
) : ReadWriteSocket, SocketBase(parent) {

    final override fun attachForReadingImpl(channel: ByteChannel): WriterJob =
        attachForReadingImpl(channel, descriptor, this, selector)

    final override fun attachForWritingImpl(channel: ByteChannel): ReaderJob =
        attachForWritingImpl(channel, descriptor, this, selector)

    override fun actualClose(): Throwable? {
        return try {
            ktor_shutdown(descriptor, ShutdownCommands.Both)
            // Close select call must happen before notifyClosed, so run undispatched.
            launch(start = CoroutineStart.UNDISPATCHED) {
                // SelectorManager could throw exception if it is closed, ignore it as notifyClosed
                // will still close the descriptor as expected.
                try {
                    selector.select(this@NativeSocketImpl, SelectInterest.CLOSE)
                } catch (_: IOException) {
                }
            }
            super.close()
            null
        } catch (cause: Throwable) {
            cause
        } finally {
            // Descriptor is closed by the selector manager
            selector.notifyClosed(this)
        }
    }
}

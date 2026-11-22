/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

/**
 * A port the kernel picks, held by a socket that binds it with SO_REUSEPORT and never listens: every reactor's
 * listener can bind it too, nothing else can take it until [close], and no connection is ever queued on it.
 */
internal expect class ReservedPort() : AutoCloseable {
    val port: Int

    override fun close()
}

/** Sleeps the calling thread, which is never a reactor's. */
internal expect fun sleepMillis(milliseconds: Long)

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * ChunkBuffer pool for UDP datagrams
 */
public val DefaultDatagramChunkBufferPool: ObjectPool<ChunkBuffer> =
    DefaultBufferPool(MAX_DATAGRAM_SIZE, 2048)

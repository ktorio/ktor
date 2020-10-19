/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*

/**
 * Byte buffer pool for UDP datagrams
 */
@ThreadLocal
@InternalAPI
public val DefaultDatagramByteBufferPool: ObjectPool<IoBuffer> =
    DefaultBufferPool(MAX_DATAGRAM_SIZE, 2048)

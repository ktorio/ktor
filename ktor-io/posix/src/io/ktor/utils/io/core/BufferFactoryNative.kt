/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.ThreadLocal

internal actual val DefaultChunkedBufferPool: ObjectPool<ChunkBuffer>
    get() = pool!!

/**
 * Initialization hack because kotlin-native init order.
 *
 * [ThreadLocal] is intialized after every single top-level variable, so acessing [ThreadLocal] in any top-level val
 * leads to BAD_ACCESS in the runtime.
 */
@ThreadLocal
internal var pool: ObjectPool<ChunkBuffer>? = null
    private set
    get() {
        if (field == null) {
            pool = DefaultBufferPool()
        }

        return field!!
    }

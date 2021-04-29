/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.*

private const val CHAR_ARRAY_POOL_SIZE = 4096

/**
 * Number of characters that a array from the pool can store
 */
internal const val CHAR_BUFFER_ARRAY_LENGTH: Int = 4096 / 2

@ThreadLocal
internal val CharArrayPool: ObjectPool<CharArray> = object : DefaultPool<CharArray>(CHAR_ARRAY_POOL_SIZE) {
    override fun produceInstance(): CharArray = CharArray(CHAR_BUFFER_ARRAY_LENGTH)
}

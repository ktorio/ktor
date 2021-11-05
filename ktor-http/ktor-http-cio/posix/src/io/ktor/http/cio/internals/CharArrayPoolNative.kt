/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
internal actual val CharArrayPool: ObjectPool<CharArray> = object : ObjectPool<CharArray> {
    override val capacity: Int
        get() = 0

    override fun borrow(): CharArray = CharArray(CHAR_BUFFER_ARRAY_LENGTH)

    override fun recycle(instance: CharArray) {
    }

    override fun dispose() {
    }
}

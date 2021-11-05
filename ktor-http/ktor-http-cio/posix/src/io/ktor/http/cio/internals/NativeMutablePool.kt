// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.*

internal object MutableDataPool : ObjectPool<MutableData> {
    override val capacity: Int = 0

    override fun borrow(): MutableData = MutableData(CHAR_BUFFER_ARRAY_LENGTH).apply {
        append(ByteArray(CHAR_BUFFER_ARRAY_LENGTH))
    }

    override fun recycle(instance: MutableData) {
    }

    override fun dispose() {
    }
}

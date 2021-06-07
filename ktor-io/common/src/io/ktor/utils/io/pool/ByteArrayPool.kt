/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.pool

import kotlin.native.concurrent.*

@ThreadLocal
public val ByteArrayPool: ObjectPool<ByteArray> = object : DefaultPool<ByteArray>(128) {
    override fun produceInstance() = ByteArray(4096)
}

/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.errors

import io.ktor.network.quic.bytes.*

internal sealed interface ErrorCode_v1 {
    val code: VarInt
}

internal enum class AppErrorCode_v1(intCode: Int) : ErrorCode_v1 {
    ;
    override val code = intCode.toVarInt()
}

internal enum class TransportErrorCode_v1(intCode: Int) : ErrorCode_v1 {
    ;
    override val code = intCode.toVarInt()
}

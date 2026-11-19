/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.ExperimentalKtorApi

@ExperimentalKtorApi
public data class DatagramReceiveResult(
    val byteCount: Int,
    val senderAddress: SocketAddress
)

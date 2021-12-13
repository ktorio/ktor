/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

public sealed class SocketAddress

public data class InetSocketAddress(public val hostname: String, public val port: Int) : SocketAddress()

public data class UnixSocketAddress(public val path: String) : SocketAddress()

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

public actual class NetworkAddress constructor(
    public val hostname: String,
    public val port: Int,
    explicitAddress: Any? = null
) {
    @InternalAPI
    public var explicitAddress: AtomicRef<Any?> = atomic(explicitAddress)

    init {
        makeShared()
    }

    /**
     * Resolve current socket address.
     */

    override fun toString(): String = "NetworkAddress[$hostname:$port]"
}

public actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress =
    NetworkAddress(hostname, port, null)

public actual val NetworkAddress.hostname: String get() = hostname

public actual val NetworkAddress.port: Int get() = port

public actual class UnresolvedAddressException : IllegalArgumentException()

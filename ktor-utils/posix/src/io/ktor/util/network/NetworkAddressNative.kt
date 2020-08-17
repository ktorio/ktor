/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.network

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

actual class NetworkAddress constructor(
    val hostname: String,
    val port: Int,
    explicitAddress: Any? = null
) {
    @InternalAPI
    var explicitAddress: AtomicRef<Any?> = atomic(explicitAddress)

    init {
        makeShared()
    }

    /**
     * Resolve current socket address.
     */

    override fun toString(): String = "NetworkAddress[$hostname:$port]"
}

actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress =
    NetworkAddress(hostname, port, null)

actual val NetworkAddress.hostname: String get() = hostname

actual val NetworkAddress.port: Int get() = port

actual class UnresolvedAddressException : IllegalArgumentException()


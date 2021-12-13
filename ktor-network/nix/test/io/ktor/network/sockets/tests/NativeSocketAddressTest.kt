/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlin.test.*

class NativeSocketAddressTest {

    @Test
    fun testResolveLocalhost() {
        val address = InetSocketAddress("localhost", 8000)
        val resolved = address.address.toSocketAddress()
        assertEquals(InetSocketAddress("::1", 8000), resolved)
    }
}

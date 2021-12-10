/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.network.util.*
import io.ktor.util.network.*
import kotlin.test.*

class NetworkAddressTest {

    @Test
    fun testNetworkAddress() {
        val address = NetworkAddress("0.0.0.0", 63033)
        assertEquals(63033, address.address.port)
        assertEquals("0.0.0.0", address.address.address)
    }
}

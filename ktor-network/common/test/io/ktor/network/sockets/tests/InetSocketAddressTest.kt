/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class InetSocketAddressTest {

    @BeforeTest
    fun setUp() {
        initSocketsIfNeeded()
    }

    @Test
    fun testResolveAddress() {
        // Address resolving is not supported on JS and WASM platforms
        if (PlatformUtils.IS_JS || PlatformUtils.IS_WASM_JS) return

        val testCases = listOf(
            "127.0.0.1" to byteArrayOf(127, 0, 0, 1),
            "::1" to byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        )

        for ((hostname, expectedBytes) in testCases) {
            val address = InetSocketAddress(hostname, 8080)
            val resolved = address.resolveAddress()
            assertContentEquals(expectedBytes, resolved, "Unexpected bytes for the address '$hostname'")
        }
    }
}

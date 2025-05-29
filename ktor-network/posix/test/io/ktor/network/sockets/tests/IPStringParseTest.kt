/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.util.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class IPStringParseTest {
    @Test
    fun testParseIPv4String() {
        val valid = listOf(
            "0.0.0.0" to byteArrayOf(0, 0, 0, 0),
            "127.0.0.1" to byteArrayOf(127, 0, 0, 1),
            "255.255.255.255" to byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()),
        )

        val invalid = listOf(
            "-1.0.0.0",
            "not an IPv4 string",
            "256.0.0.0",
            "300",
            "::"
        )

        valid.forEach {
            val parsed = parseIPv4String(it.first)
            assertContentEquals(parsed, it.second)
        }

        invalid.forEach {
            val parsed = parseIPv4String(it)
            assertNull(parsed)
        }
    }

    @Test
    fun testParseIPv6String() {
        val valid = listOf(
            "::" to ByteArray(16) { 0 },
            "2001:0db8:85a3::8a2e:0370:7334" to byteArrayOf(
                0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
                0x00, 0x00, 0x8a.toByte(), 0x2e, 0x03, 0x70, 0x73, 0x34
            ),
            "fe80::1" to byteArrayOf(
                0xfe.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
            ),
            "::1" to byteArrayOf(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
            ),
            "2001:db8::ff00:42:8329" to byteArrayOf(
                0x20, 0x01, 0x0d, 0xb8.toByte(), 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0xff.toByte(), 0x00, 0x00, 0x42, 0x83.toByte(), 0x29
            )
        )

        val invalid = listOf(
            ":",
            "0:0:1",
            "not an IPv6 string",
            "255.255.255.255",
            "-1::0"
        )

        valid.forEach {
            val parsed = parseIPv6String(it.first)
            assertContentEquals(parsed, it.second)
        }

        invalid.forEach {
            val parsed = parseIPv6String(it)
            assertNull(parsed)
        }
    }
}

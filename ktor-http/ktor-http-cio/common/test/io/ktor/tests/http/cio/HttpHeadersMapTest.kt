/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpHeadersMapTest {

    @Test
    fun testEmptyHeaders() {
        val rawHeaders = ""
        val builder = CharArrayBuilder().also { it.append(rawHeaders) }
        val headers = HttpHeadersMap(builder)

        try {
            assertEquals(0, headers.size)
            assertEquals(null, headers["key1"])
            assertEquals(emptyList(), headers.getAll("key2").toList())

            assertEquals(emptyList(), headers.offsets().toList())
        } finally {
            headers.release()
        }
    }

    @Test
    fun testPutOneValuePerKey(): Unit = test {
        val rawHeaders = "key1: 1; key2: 2; key3: 3"
        val builder = CharArrayBuilder().also { it.append(rawHeaders) }
        val headers = HttpHeadersMap(builder)
        val k1Start = rawHeaders.indexOf("key1")
        val k2Start = rawHeaders.indexOf("key2")
        val k3Start = rawHeaders.indexOf("key3")
        val v1Start = rawHeaders.indexOf("1")
        val v2Start = rawHeaders.indexOf("2")
        val v3Start = rawHeaders.indexOf("3")

        try {
            headers.put(k1Start, k1Start + 4, v1Start, v1Start + 1)
            headers.put(k2Start, k2Start + 4, v2Start, v2Start + 1)
            headers.put(k3Start, k3Start + 4, v3Start, v3Start + 1)

            assertEquals("1", headers["key1"].toString())
            assertEquals(1, headers.getAll("key1").toList().size)
            assertEquals("2", headers["key2"].toString())
            assertEquals(1, headers.getAll("key2").toList().size)
            assertEquals("3", headers["key3"].toString())
            assertEquals(1, headers.getAll("key3").toList().size)

            val offsets = headers.offsets()
            val keys = offsets.map { headers.nameAtOffset(it).toString() }.toList()
            val values = offsets.map { headers.valueAtOffset(it).toString() }.toList()

            assertTrue(keys.containsAll(listOf("key1", "key2", "key3")))
            assertTrue(values.containsAll(listOf("1", "2", "3")))
        } finally {
            headers.release()
        }
    }

    @Test
    fun testPutMultipleValuesPerKey(): Unit = test {
        val rawHeaders = "keyEven: 0,2,4,6; keyOdd: 1,3,5,7"
        val builder = CharArrayBuilder().also { it.append(rawHeaders) }
        val headers = HttpHeadersMap(builder)
        val kEvenStart = rawHeaders.indexOf("keyEven")
        val kOddStart = rawHeaders.indexOf("keyOdd")
        val v0Start = rawHeaders.indexOf("0")
        val v1Start = rawHeaders.indexOf("1")
        val v2Start = rawHeaders.indexOf("2")
        val v3Start = rawHeaders.indexOf("3")
        val v4Start = rawHeaders.indexOf("4")
        val v5Start = rawHeaders.indexOf("5")
        val v6Start = rawHeaders.indexOf("6")
        val v7Start = rawHeaders.indexOf("7")

        try {
            headers.put(kEvenStart, kEvenStart + 7, v0Start, v0Start + 1)
            headers.put(kEvenStart, kEvenStart + 7, v2Start, v2Start + 1)
            headers.put(kEvenStart, kEvenStart + 7, v4Start, v4Start + 1)
            headers.put(kEvenStart, kEvenStart + 7, v6Start, v6Start + 1)
            headers.put(kOddStart, kOddStart + 6, v1Start, v1Start + 1)
            headers.put(kOddStart, kOddStart + 6, v3Start, v3Start + 1)
            headers.put(kOddStart, kOddStart + 6, v5Start, v5Start + 1)
            headers.put(kOddStart, kOddStart + 6, v7Start, v7Start + 1)

            assertEquals("0", headers["keyEven"].toString())
            assertEquals(listOf("0", "2", "4", "6"), headers.getAll("keyEven").map { it.toString() }.toList())
            assertEquals("1", headers["keyOdd"].toString())
            assertEquals(listOf("1", "3", "5", "7"), headers.getAll("keyOdd").map { it.toString() }.toList())

            val offsets = headers.offsets()
            val keys = offsets.map { headers.nameAtOffset(it).toString() }.toList()
            val values = offsets.map { headers.valueAtOffset(it).toString() }.toList()

            assertTrue(keys.containsAll(listOf("keyEven", "keyOdd")))
            assertTrue(values.containsAll(listOf("0", "1", "2", "3", "4", "5", "6", "7")))

            assertEquals(keys, (0 until headers.size).map { headers.nameAt(it).toString() }.toList())
            assertEquals(values, (0 until headers.size).map { headers.valueAt(it).toString() }.toList())

            val idxOfOne = (0 until headers.size).first { headers.valueAt(it).toString() == "1" }
            assertEquals("3", headers.valueAt(headers.find("keyOdd", idxOfOne + 1)).toString())
            val idxOfTwo = (0 until headers.size).first { headers.valueAt(it).toString() == "2" }
            assertEquals("4", headers.valueAt(headers.find("keyEven", idxOfTwo + 1)).toString())

            val idxOfSix = (0 until headers.size).first { headers.valueAt(it).toString() == "6" }
            assertEquals(-1, headers.find("keyEven", idxOfSix + 1))
        } finally {
            headers.release()
        }
    }

    @Test
    fun testHeadersMapResize() = test {
        val keyLength = 30
        val headersCount = 1000
        val random = Random(42)

        val builder = CharArrayBuilder()
        val headers = HttpHeadersMap(builder)
        val keys = mutableListOf<String>()

        try {
            for (i in 0 until headersCount) {
                val randomKey =
                    ByteArray(keyLength).also { random.nextBytes(it) }.map { it.toInt().toChar() }.joinToString("")
                builder.append(randomKey)
                headers.put(i * keyLength, (i * keyLength) + keyLength, i * keyLength, (i * keyLength) + keyLength)
                headers.put(i * keyLength, (i * keyLength) + keyLength, i * keyLength, (i * keyLength) + keyLength)
                keys.add(randomKey)
            }
            assertEquals(headersCount * 2, headers.size)

            for (i in 0 until headersCount) {
                headers.put(i * keyLength, (i * keyLength) + keyLength, i * keyLength, (i * keyLength) + keyLength)
            }
            assertEquals(headersCount * 3, headers.size)

            for (key in keys) {
                val all = headers.getAll(key).toList()
                assertEquals(3, all.size)
                assertTrue(all.all { it == key })
            }

            assertTrue(keys.containsAll(headers.offsets().map { headers.nameAtOffset(it).toString() }.toList()))
        } finally {
            headers.release()
        }
    }

    @Test
    fun testHashCollisions() = test {
        val names = listOf(
            "origin",
            "q4igin",
            "content-length",
            "3chh57k-length",
            "transfer-encoding",
            "trc25fer-encoding"
        )
        val namesString = names.joinToString(";")
        val builder = CharArrayBuilder().also { it.append(namesString) }
        val headers = HttpHeadersMap(builder)

        val origin1 = namesString.indexOf(names[0])
        val origin2 = namesString.indexOf(names[1])
        val length1 = namesString.indexOf(names[2])
        val length2 = namesString.indexOf(names[3])
        val transfer1 = namesString.indexOf(names[4])
        val transfer2 = namesString.indexOf(names[5])

        try {
            // Test that there exists hash codes collision
            assertEquals(
                builder.hashCodeLowerCase(origin1, origin1 + names[0].length),
                builder.hashCodeLowerCase(origin2, origin2 + names[1].length)
            )
            assertEquals(
                builder.hashCodeLowerCase(length1, length1 + names[2].length),
                builder.hashCodeLowerCase(length2, length2 + names[3].length)
            )
            assertEquals(
                builder.hashCodeLowerCase(transfer1, transfer1 + names[4].length),
                builder.hashCodeLowerCase(transfer2, transfer2 + names[5].length)
            )

            headers.put(origin1, origin1 + names[0].length, origin1, origin1 + names[0].length)
            headers.put(origin2, origin2 + names[1].length, origin2, origin2 + names[1].length)
            headers.put(length1, length1 + names[2].length, length1, length1 + names[2].length)
            headers.put(length2, length2 + names[3].length, length2, length2 + names[3].length)
            headers.put(transfer1, transfer1 + names[4].length, transfer1, transfer1 + names[4].length)
            headers.put(transfer2, transfer2 + names[5].length, transfer2, transfer2 + names[5].length)

            // check that values are not influenced
            for (name in names) {
                assertEquals(name, headers[name].toString())
                assertEquals(1, headers.getAll(name).toList().size)
            }
        } finally {
            headers.release()
        }
    }

    @Test
    fun testHeadersMapRelease() = test {
        val builder = CharArrayBuilder()
        (0..1000).map { HttpHeadersMap(builder) }.toList()
        builder.release()
    }
}

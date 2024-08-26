/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.header

import io.ktor.http.*
import io.ktor.http.header.*
import kotlin.test.*

class AcceptEncodingTest {

    @Test
    fun testAcceptEncoding() {
        assertEquals(AcceptEncoding.gzip.toString(), "gzip")
    }

    @Test
    fun testAcceptEncodingWithQValue() {
        assertEquals(AcceptEncoding.gzip.withQValue(0.5).toString(), "gzip; q=0.5")
    }

    @Test
    fun testAcceptEncodingWithDifferentQValue2Times() {
        val acceptEncoding = AcceptEncoding.gzip.withQValue(0.2)
        assertEquals(acceptEncoding.withQValue(0.5).toString(), "gzip; q=0.5")
    }

    @Test
    fun testAcceptEncodingConstructorCreation() {
        assertEquals(AcceptEncoding("gzip", listOf(HeaderValueParam("q", "0.2"))).toString(), "gzip; q=0.2")
    }

    @Test
    fun testAcceptEncodingMerge() {
        assertEquals(AcceptEncoding.mergeAcceptEncodings(
            AcceptEncoding.gzip, AcceptEncoding.deflate.withQValue(0.2), AcceptEncoding.all.withQValue(0.3)),
            "gzip, deflate; q=0.2, *; q=0.3")
    }

    @Test
    fun testAcceptEncodingEquals() {
        val gzip = AcceptEncoding("gzip")
        val gzip2 = AcceptEncoding("gzip")
        val deflate = AcceptEncoding("deflate")
        val gzipUppercase = AcceptEncoding("GZIP")
        val gzipWithQValue = AcceptEncoding("gzip").withQValue(0.3)
        val gzipWithQValue2 = AcceptEncoding("gzip").withQValue(0.3)

        assertEquals(gzip, gzip2)
        assertEquals(gzip, gzipUppercase)
        assertNotEquals(gzip, deflate)
        assertEquals(gzipWithQValue, gzipWithQValue2)
        assertNotEquals(gzipWithQValue, gzip)
    }

    @Test
    fun testAcceptEncodingHashCode() {
        val gzip = AcceptEncoding("gzip")
        val gzip2 = AcceptEncoding("gzip")
        val deflate = AcceptEncoding("deflate")
        val gzipWithQValue = AcceptEncoding("gzip").withQValue(0.4)
        val gzipWithQValue2 = AcceptEncoding("gzip").withQValue(0.4)

        assertEquals(gzip.hashCode(), gzip2.hashCode())
        assertNotEquals(gzip.hashCode(), deflate.hashCode())
        assertEquals(gzipWithQValue.hashCode(), gzipWithQValue2.hashCode())
        assertNotEquals(gzip, gzipWithQValue)
    }

    @Test
    fun testAcceptEncodingQValueComparisonIgnoreCase() {
        val encoding1 = AcceptEncoding("gzip", listOf(HeaderValueParam("q", "0.5")))
        val encoding2 = encoding1.withQValue(0.5)

        assertEquals(encoding1, encoding2)
    }

    @Test
    fun testAcceptEncodingQValueWithDifferentCases() {
        val encoding1 = AcceptEncoding("gzip", listOf(HeaderValueParam("Q", "0.5")))
        val encoding2 = AcceptEncoding("gzip", listOf(HeaderValueParam("q", "0.5")))

        assertEquals(encoding1, encoding2)
    }

}

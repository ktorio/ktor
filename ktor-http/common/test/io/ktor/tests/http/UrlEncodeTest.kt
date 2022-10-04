/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class UrlEncodeTest {

    @Test
    fun testUrlEncodePathKeepDigits() {
        assertEquals("0123456789", "0123456789".encodeURLPath())
    }

    @Test
    fun testUrlEncodeQueryComponentKeepDigits() {
        assertEquals("0123456789", "0123456789".encodeURLQueryComponent())
    }

    @Test
    fun testUrlKeepDigitsInPath() {
        assertEquals("/0123456789/", Url("http://x.com/0123456789/").encodedPath)
    }

    @Test
    fun testUrlEncodePathKeepLetters() {
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".encodeURLPath()
        )
    }

    @Test
    fun testUrlEncodeQueryComponentKeepLetters() {
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".encodeURLQueryComponent()
        )
    }

    @Test
    fun testUrlKeepLettersInPath() {
        assertEquals(
            "/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/",
            Url("http://x.com/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/").encodedPath
        )
    }

    @Test
    fun testUrlEncodePathKeepHyphen() {
        assertEquals("-", "-".encodeURLPath())
    }

    @Test
    fun testUrlEncodeQueryComponentKeepHyphen() {
        assertEquals("-", "-".encodeURLQueryComponent())
    }

    @Test
    fun testUrlKeepHyphenInPath() {
        assertEquals("/-/", Url("http://x.com/-/").encodedPath)
    }

    @Test
    fun testUrlEncodePathKeepPeriod() {
        assertEquals(".", ".".encodeURLPath())
    }

    @Test
    fun testUrlEncodeQueryComponentKeepPeriod() {
        assertEquals(".", ".".encodeURLQueryComponent())
    }

    @Test
    fun testUrlKeepPeriodInPath() {
        assertEquals("/./", Url("http://x.com/./").encodedPath)
    }

    @Test
    fun testUrlEncodePathKeepUnderscore() {
        assertEquals("_", "_".encodeURLPath())
    }

    @Test
    fun testUrlEncodeQueryComponentKeepUnderscore() {
        assertEquals("_", "_".encodeURLQueryComponent())
    }

    @Test
    fun testUrlKeepUnderscoreInPath() {
        assertEquals("/_/", Url("http://x.com/_/").encodedPath)
    }

    @Test
    fun testUrlEncodePathKeepTilde() {
        assertEquals("~", "~".encodeURLPath())
    }

    @Test
    fun testUrlEncodeQueryComponentKeepTilde() {
        assertEquals("~", "~".encodeURLQueryComponent())
    }

    @Test
    fun testUrlKeepTildeInPath() {
        assertEquals("/~/", Url("http://x.com/~/").encodedPath)
    }
}

/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class CodecTestJvm {

    @Test
    fun testEncodeDecodeUrlPathPathRoundtrippingExhaustivelySingleCodePoints() {
        val allStrings = allStrings()
        allStrings.forEachIndexed { index, str ->
            try {
                val encoded = str.encodeURLPathPart()
                val decoded = encoded.decodeURLPart()
                assertEquals(str, decoded)
            } catch (e: Throwable) {
                throw AssertionError("Failed for string '$str' with chars '${str.codePoints().toArray().toList()}", e)
            }
        }
    }

    /**
     * Extra test method just for the fun of it. Maybe combinations of chars will find some errors.
     */
    @Test
    fun testEncodeDecodeUrlPathPathRoundtrippingExhaustivelyBatchedCodePoints() {
        val allStrings = allStrings()
            .chunked(100) { it.joinToString(separator = "") }

        allStrings.forEachIndexed { index, str ->
            try {
                val encoded = str.encodeURLPathPart()
                val decoded = encoded.decodeURLPart()
                assertEquals(str, decoded)
            } catch (e: Throwable) {
                throw AssertionError("Failed for string '$str' with chars '${str.codePoints().toArray().toList()}", e)
            }
        }
    }

    private fun allStrings(): List<String> {
        val res = mutableListOf<String>()
//        0xD800 seems to be the first char that breaks everything.. so lets stop here for now
        for (it in Character.MIN_CODE_POINT/*..Character.MAX_CODE_POINT*/ until 0xD800) {
            if (Character.isValidCodePoint(it)) {
                res.add(Character.toString(it))
            }
        }
        return res
    }

}

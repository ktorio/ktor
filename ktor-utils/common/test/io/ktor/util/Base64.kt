/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlin.test.*

class Base64 {

    @Test
    fun encodeDecodeTextTest() {
        val text = "This reference is designed for you to easily learn Kotlin in a matter of hours. " +
            "Start with the basic syntax, then proceed to more advanced topics. While reading, " +
            "you can try out the examples in the online IDE. " +
            "Once you get an idea of what Kotlin looks like, " +
            "try solving some Kotlin Koans - interactive programming exercises. " +
            "If you are not sure how to solve a Koan, " +
            "or you're looking for a more elegant solution, check out Kotlin idioms."

        val encodedText = "VGhpcyByZWZlcmVuY2UgaXMgZGVzaWduZWQgZm9yIHlvdSB0byBlYXNpbHkgbGVhcm4gS290bGluIG" +
            "luIGEgbWF0dGVyIG9mIGhvdXJzLiBTdGFydCB3aXRoIHRoZSBiYXNpYyBzeW50YXgsIHRoZW4gcHJvY2VlZCB0by" +
            "Btb3JlIGFkdmFuY2VkIHRvcGljcy4gV2hpbGUgcmVhZGluZywgeW91IGNhbiB0cnkgb3V0IHRoZSBleGFtcGxlcy" +
            "BpbiB0aGUgb25saW5lIElERS4gT25jZSB5b3UgZ2V0IGFuIGlkZWEgb2Ygd2hhdCBLb3RsaW4gbG9va3MgbGlrZS" +
            "wgdHJ5IHNvbHZpbmcgc29tZSBLb3RsaW4gS29hbnMgLSBpbnRlcmFjdGl2ZSBwcm9ncmFtbWluZyBleGVyY2lzZX" +
            "MuIElmIHlvdSBhcmUgbm90IHN1cmUgaG93IHRvIHNvbHZlIGEgS29hbiwgb3IgeW91J3JlIGxvb2tpbmcgZm9yIG" +
            "EgbW9yZSBlbGVnYW50IHNvbHV0aW9uLCBjaGVjayBvdXQgS290bGluIGlkaW9tcy4="

        assertEquals(encodedText, text.encodeBase64())
        assertEquals(text, encodedText.decodeBase64String())
    }

    @Test
    fun encodeEmptyTest() {
        assertEquals("", "".encodeBase64())
        assertEquals("", "".decodeBase64String())
    }

    @Test
    fun paddingTest() {
        val cases = mapOf(
            "This" to "VGhpcw==",
            "Thi" to "VGhp",
            "Th" to "VGg=",
            "T" to "VA==",
            "" to ""
        )

        cases.forEach { (text, encodedText) ->
            assertEquals(encodedText, text.encodeBase64())
            assertEquals(text, encodedText.decodeBase64String())
        }
    }
}

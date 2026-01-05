/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import kotlinx.cinterop.*
import libcurl.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class LibcurlTest {

    @Test
    fun `test all required features present`() {
        // See https://curl.se/libcurl/c/curl_version_info.html#FEATURES
        val requiredFeatures = setOf(
            "IPv6",
            "HTTP2",
            "SSL",
        )

        val features = getCurlVersionInfo().feature_names?.toStringSet().orEmpty()
        val missingFeatures = requiredFeatures - features
        assertTrue(missingFeatures.isEmpty(), "Missing features: ${missingFeatures.joinToString()}")
    }

    @Test
    fun `test all required protocols supported`() {
        val requiredProtocols = setOf("http", "https", "ws", "wss")

        val protocols = getCurlVersionInfo().protocols?.toStringSet().orEmpty()
        val missingProtocols = requiredProtocols - protocols
        assertTrue(missingProtocols.isEmpty(), "Missing protocols: ${missingProtocols.joinToString()}")
    }

    @OptIn(ExperimentalNativeApi::class)
    @Test
    fun `test CURLINFO_CAPATH or CURLINFO_CAINFO set`() = memScoped {
        // libcurl doesn't set default CAPATH/CAINFO on Windows
        if (Platform.osFamily == OsFamily.WINDOWS) return

        val curl = curl_easy_init()

        val caPath = allocPointerTo<ByteVar>()
        curl_easy_getinfo(curl, CURLINFO_CAPATH, caPath.ptr)

        val caInfo = allocPointerTo<ByteVar>()
        curl_easy_getinfo(curl, CURLINFO_CAINFO, caInfo.ptr)

        curl_easy_cleanup(curl)

        assertFalse(caPath.value == null && caInfo.value == null, "Neither CURLINFO_CAPATH nor CURLINFO_CAINFO are set")
    }

    @Suppress("DEPRECATION")
    private fun getCurlVersionInfo(): curl_version_info_data =
        curl_version_info(CURLversion.byValue(CURLVERSION_NOW.toUInt()))!!.pointed

    private fun CPointer<CPointerVar<ByteVar>>.toStringSet(): Set<String> = buildSet {
        var index = 0
        while (true) {
            val value = get(index) ?: break
            add(value.toKString())
            index++
        }
    }
}

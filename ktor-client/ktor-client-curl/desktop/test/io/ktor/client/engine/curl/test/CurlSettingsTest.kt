/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import kotlinx.cinterop.*
import libcurl.curl_easy_cleanup
import libcurl.curl_easy_getinfo
import libcurl.curl_easy_init
import kotlin.test.Test

@OptIn(ExperimentalForeignApi::class)
class CurlSettingsTest {

    @Test
    fun `test CURLINFO_CAPATH and CURLINFO_CAINFO`() = memScoped {
        val curl = curl_easy_init()

        val caPath = allocPointerTo<ByteVar>()
        curl_easy_getinfo(curl, libcurl.CURLINFO_CAPATH, caPath.ptr)
        println("CURLINFO_CAPATH: " + caPath.value?.toKString())

        val caInfo = allocPointerTo<ByteVar>()
        curl_easy_getinfo(curl, libcurl.CURLINFO_CAINFO, caInfo.ptr)
        println("CURLINFO_CAINFO:" + caInfo.value?.toKString())

        curl_easy_cleanup(curl)
    }
}

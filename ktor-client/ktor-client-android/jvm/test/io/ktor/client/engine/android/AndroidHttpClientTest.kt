/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.tests.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidHttpClientTest : HttpClientTest(Android) {

    @Test
    fun checkPlatformConfig() {
        val client = HttpClient(Android)
        client.close()
        if (System.getProperty("java.vm.name") != "Dalvik") return
        val segmentPoolSize = System.getProperty("kotlinx.io.pool.size.bytes")
        assertEquals("2097152", segmentPoolSize, "Default segment pool should be assigned")
    }
}

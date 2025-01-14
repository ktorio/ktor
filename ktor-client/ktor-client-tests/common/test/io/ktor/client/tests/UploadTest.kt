/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadTest : ClientLoader() {

    @Test
    fun testUploadWithByteArrayContent() = clientTests(except("Android", "Curl", "Darwin", "DarwinLegacy")) {
        test { client ->
            val result = client.post("$TEST_SERVER/upload/content") {
                setBody(ByteArrayContent(ByteArray(1024)))
            }.body<String>()

            assertEquals("EMPTY", result)
        }
    }

    @Test
    fun testUploadWithEmptyContentType() = clientTests {
        test { client ->
            val result = client.post("$TEST_SERVER/upload/content") {
                setBody(ByteArrayContent(ByteArray(1024), ContentType("", "")))
            }.body<String>()

            assertEquals("/", result)
        }
    }
}

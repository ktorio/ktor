/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.content.*
import io.ktor.http.*
import kotlin.test.*

class UploadTest : ClientLoader() {

    @Test
    fun testUploadWithByteArrayContent() = clientTests(listOf("Android", "Curl")) {
        test { client ->
            val result = client.post<String>("$TEST_SERVER/upload/content") {
                body = ByteArrayContent(ByteArray(1024))
            }

            assertEquals("EMPTY", result)
        }
    }

    @Test
    fun testUploadWithEmptyContentType() = clientTests {
        test { client ->
            val result = client.post<String>("$TEST_SERVER/upload/content") {
                body = ByteArrayContent(ByteArray(1024), ContentType("", ""))
            }

            assertEquals("/", result)
        }
    }
}

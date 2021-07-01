/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

private const val TEST_URL = "$TEST_SERVER/compression"

class ContentEncodingTest : ClientLoader() {
    @Test
    fun testIdentity() = clientTests {
        config {
            ContentEncoding {
                identity()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/identity").body<String>()
            assertEquals("Compressed response!", response)
        }
    }

    @Test
    fun testDeflate() = clientTests {
        config {
            ContentEncoding {
                deflate()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/deflate").body<String>()
            assertEquals("Compressed response!", response)
        }
    }

    @Test
    fun testGZip() = clientTests {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip").body<String>()
            assertEquals("Compressed response!", response)
        }
    }
}

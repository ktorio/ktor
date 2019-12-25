/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.util.*
import kotlin.test.*

class AttributesTest : ClientLoader() {
    @Test
    fun testKeepAttributes() = clientTests {
        val attrKey = AttributeKey<String>("my-key")

        config {
            install("attr-test") {
                receivePipeline.intercept(HttpReceivePipeline.After) {
                    val attr = it.call.request.attributes[attrKey]

                    assertEquals("test-data", attr)
                }
            }
        }

        test { client ->
            val response = client.get<String>("$TEST_SERVER/content/hello") {
                setAttributes {
                    put(attrKey, "test-data")
                }
            }

            assertEquals("hello", response)
        }
    }
}

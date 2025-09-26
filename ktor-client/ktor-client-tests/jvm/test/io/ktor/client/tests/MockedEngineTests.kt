/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Content-Length is skipped for Darwin and JS/WASM platforms, because it conflicts with the platform's automatic decoding.
 */
class MockEngineTestsJvm {

    @Test
    fun testContentLengthIsCheckedForByteArray() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    respond("hello", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.prepareGet(Url("http://host")) {
                    url.path("path")
                }.execute { response ->
                    response.body<ByteArray>()
                }
            }
        }
    }

    @Test
    fun testContentLengthIsChecked() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    respond("hello", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.get("https://host/path").body<String>()
            }
        }
    }
}

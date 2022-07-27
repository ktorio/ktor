/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

class DefaultTransformTest {

    @Test
    fun testVerifyByteArrayLength() = testSuspend {
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond("hello", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            httpClient.get("http://host/path").body<ByteArray>()
        }
    }
}

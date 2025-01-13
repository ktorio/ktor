/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryTest : ClientLoader() {
    @Test
    fun queryParametersArentModified() = clientTests {
        test { client ->
            val result = client.get {
                url {
                    encodedParameters.apply {
                        url.encodedParameters.appendAll("", emptyList())
                        url.takeFrom("$TEST_SERVER/content/uri?Expires")
                    }
                }
            }.bodyAsText()
            assertEquals("/content/uri?&Expires", result)
        }
    }
}

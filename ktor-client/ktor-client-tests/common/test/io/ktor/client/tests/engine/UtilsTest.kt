/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.engine

import io.ktor.client.engine.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlin.test.*

class UtilsTest {
    @Test
    fun testMergeHeaders() {
        val headers = HeadersBuilder().apply {
            append("Accept", "application/xml")
            append("Accept", "application/json")
        }

        val result = mutableMapOf<String, String>()
        mergeHeaders(headers.build(), EmptyContent) { key, value ->
            result[key] = value
        }

        assertEquals("application/xml,application/json", result["Accept"])
    }
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.request.*
import kotlin.test.*

class HttpStatementTest {

    @Test
    fun testStatementToString() {
        val builder = HttpRequestBuilder()
        val statement = HttpStatement(builder, HttpClient(TestEngine))

        assertEquals("HttpStatement[${builder.url.buildString()}]", statement.toString())
        assertEquals("HttpStatement[${builder.url.buildString()}]", statement.toString())
    }
}

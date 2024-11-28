/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

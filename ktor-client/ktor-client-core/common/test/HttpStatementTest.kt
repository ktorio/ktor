import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.test.*

/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class HttpStatementTest {

    @Test
    fun testStatementToString() {
        val statement = HttpStatement(HttpRequestBuilder(), HttpClient(TestEngine))

        assertEquals("HttpStatement[http://localhost/]", statement.toString())
        assertEquals("HttpStatement[http://localhost/]", statement.toString())
    }
}


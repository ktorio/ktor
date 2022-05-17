import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DarwinEngineTest {

    @Test
    fun testRequestInRunBlocking() = runBlocking {
        val client = HttpClient(Darwin)

        try {
            withTimeout(1000) {
                val response = client.get("http://127.0.0.1:8080")
                assertEquals("Hello, world!", response.bodyAsText())
            }
        } finally {
            client.close()
        }
    }
}

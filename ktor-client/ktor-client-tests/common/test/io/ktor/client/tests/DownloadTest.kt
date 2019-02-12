package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*


class DownloadTest {
    @Test
    fun testDownloadGoogle() = clientsTest {
        test { client ->
            val response = client.get<String>("http://www.google.com/")
            assertTrue { response.isNotEmpty() }
        }
    }

    @Test
    fun testLocalhostEcho() = clientsTest {
        val text = "Hello, world"
        test { client ->
            val response = client.post<String>("http://0.0.0.0:8080/echo") {
                body = text
            }

            assertEquals(text, response)
        }
    }
}

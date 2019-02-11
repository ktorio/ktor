package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*


class DownloadTest {
    @Test
    fun downloadGoogle() = clientTest {
        test { client ->
            val response = client.get<String>("http://www.google.com/")
            assertTrue { response.isNotEmpty() }
        }
    }
}

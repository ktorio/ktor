package io.ktor.client.tests

import io.ktor.client.*
import kotlin.test.*

class DefaultEngineTest {
    @Test
    fun instantiationTest() {
        val client = HttpClient()
        client.close()
    }
}

package io.ktor.client.features.logging

import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class RemoteTests {

    @Test
    fun testDownloadWithNoneLogLevel() = clientsTest {
        config {
            install(Logging) {
                level = LogLevel.NONE
            }
        }

        test { client ->
            val size = 4 * 1024 * 1024
            client.get<HttpResponse>("$TEST_SERVER/bytes?size=$size").use {
                assertEquals(size, it.readBytes().size)
            }
        }
    }
}

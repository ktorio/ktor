package io.ktor.client.features.compression

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

private const val TEST_URL = "$TEST_SERVER/compression"

class ContentEncodingTest {
    @Test
    fun testIdentity() {
        clientsTest {
            config {
                ContentEncoding {
                    identity()
                }
            }

            test { client ->
                val response = client.get<String>("$TEST_URL/identity")
                assertEquals("Compressed response!", response)
            }
        }
    }

    @Test
    fun testDeflate() = clientsTest {
        config {
            ContentEncoding {
                deflate()
            }
        }

        test { client ->
            val response = client.get<String>("$TEST_URL/deflate")
            assertEquals("Compressed response!", response)
        }
    }

    @Test
    fun testGZip() = clientsTest {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get<String>("$TEST_URL/gzip")
            assertEquals("Compressed response!", response)
        }
    }
}

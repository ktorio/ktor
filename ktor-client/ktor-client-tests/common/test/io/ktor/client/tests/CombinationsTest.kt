package io.ktor.client.tests

import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.test.*

@Serializable
class TestClass(val test: String)

class CombinationsTest {

    @Test
    fun testAuthJsonLogging() = clientsTest {
        config {
            Logging {
                level = LogLevel.ALL
            }

            Auth {
                basic {
                    realm = "my-server"
                    username = "user1"
                    password = "Password1"
                }
            }

            Json {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val code = client.post<HttpStatusCode>("$TEST_SERVER/auth/basic") {
                contentType(ContentType.Application.Json)
                body = TestClass("text")
            }

            assertEquals(HttpStatusCode.OK, code)
        }
    }
}

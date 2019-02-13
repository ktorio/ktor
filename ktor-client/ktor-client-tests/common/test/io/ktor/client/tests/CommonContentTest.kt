package io.ktor.client.tests

import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CommonContentTest {

    @Test
    fun testPostWithEmptyBody() = clientsTest {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                contentType(ContentType.Application.Json)
            }

            assertEquals("{}", response)
        }
    }
}

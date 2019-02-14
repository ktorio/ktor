package io.ktor.client.features.json

import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@Serializable
data class GithubProfile(
    val login: String,
    val id: Int,
    val name: String
)

class KotlinxSerializerTest {
    @Test
    fun testRegisterCustom() {
        val serializer = KotlinxSerializer().apply {
            @UseExperimental(ImplicitReflectionSerializer::class)
            register(User.serializer())
        }

        val user = User(1, "vasya")
        val actual = serializer.testWrite(user)
        assertEquals("{\"id\":1,\"login\":\"vasya\"}", actual)
    }

    @Test
    fun testRegisterCustomList() {
        val serializer = KotlinxSerializer().apply {
            registerList(User.serializer())
            registerList(Photo.serializer())
        }

        val user = User(2, "petya")
        val photo = Photo(3, "petya.jpg")

        assertEquals("[{\"id\":2,\"login\":\"petya\"}]", serializer.testWrite(listOf(user)))
        assertEquals("[{\"id\":3,\"path\":\"petya.jpg\"}]", serializer.testWrite(listOf(photo)))
    }

    @Test
    fun testReceiveFromGithub() = clientsTest {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json.nonstrict).apply {
                    register(GithubProfile.serializer())
                }
            }
        }

        test { client ->
            val e5l = GithubProfile("e5l", 4290035, "Leonid Stashevsky")
            assertEquals(e5l, client.get("https://api.github.com/users/e5l"))
        }
    }

    @Test
    fun testCustomFormBody() = clientsTest {
        config {
            install(JsonFeature)
        }

        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeStringUtf8("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        test { client ->
            var throwed = false
            try {
                client.submitFormWithBinaryData<String>(url = "upload", formData = data())
            } catch (cause: Throwable) {
                throwed = true
            }
            assertTrue(throwed)
        }

    }

    private fun JsonSerializer.testWrite(data: Any): String =
        (write(data) as? TextContent)?.text ?: error("Failed to get serialized $data")
}

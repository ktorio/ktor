package io.ktor.client.features.json

import io.ktor.client.call.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.serialization.*
import kotlin.coroutines.*
import kotlin.jvm.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

class KotlinxSerializerTest {

    @Test
    fun registerCustomTest() {
        val serializer = KotlinxSerializer().apply {
            @UseExperimental(ImplicitReflectionSerializer::class)
            register(User.serializer())
        }

        val user = User(1, "vasya")
        val actual = serializer.testWrite(user)
        assertEquals("{\"id\":1,\"login\":\"vasya\"}", actual)
//        assertEquals(user, serializer.testRead(actual))
    }

    @Test
    fun registerCustomListTest() {
        val serializer = KotlinxSerializer().apply {
            registerList(User.serializer())
            registerList(Photo.serializer())
        }

        val user = User(2, "petya")
        val photo = Photo(3, "petya.jpg")

        assertEquals("[{\"id\":2,\"login\":\"petya\"}]", serializer.testWrite(listOf(user)))
        assertEquals("[{\"id\":3,\"path\":\"petya.jpg\"}]", serializer.testWrite(listOf(photo)))
    }

    private fun JsonSerializer.testWrite(data: Any): String =
        (write(data) as? TextContent)?.text ?: error("Failed to get serialized $data")

    private suspend inline fun <reified T : Any> JsonSerializer.testRead(data: String): T {
        val info = typeInfo<T>()

        val response = object : HttpResponse {
            override val call: HttpClientCall get() = TODO()
            override val status: HttpStatusCode get() = TODO()
            override val version: HttpProtocolVersion get() = TODO()
            override val requestTime: GMTDate get() = TODO()
            override val responseTime: GMTDate get() = TODO()
            override val headers: Headers get() = TODO()
            override val coroutineContext: CoroutineContext get() = TODO()

            override val content: ByteReadChannel = ByteReadChannel(data)
        }

        return read(info, response) as T
    }
}

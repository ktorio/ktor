package io.ktor.client.tests

import com.google.gson.*
import io.ktor.client.*
import io.ktor.client.backend.jvm.*
import io.ktor.client.features.json.*
import io.ktor.client.pipeline.*
import io.ktor.client.tests.utils.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Assert.assertEquals


class JsonTests : TestWithKtor() {
    override val server: ApplicationHost = embeddedServer(Jetty, 8080) {
        routing {
            get("/") {
                val text = call.receiveText()
                val request = Gson().fromJson(text, Request::class.java)
                call.respondText(
                        Response(request.id, request.name).serialize(),
                        ContentType.Application.Json
                )
            }
        }
    }

    data class Request(val id: Int, val name: String? = null)

    data class Response(val requestId: Int, val name: String? = null)

    @Test
    fun simpleJson() {
        val client = HttpClient(ApacheBackend).config {
            install(Json)
        }

        simpleTest(client)
        client.close()
    }

    @Test
    fun simpleGson() {
        val client = HttpClient(ApacheBackend).config {
            install(Json) {
                serializer = GsonSerializer()
            }
        }

        simpleTest(client)
        client.close()
    }


    private fun simpleTest(client: HttpClient) {
        val request = Request(1)
        val response = runBlocking {
            client.get<Response>(port = 8080, payload = request.serialize())
        }

        assertEquals(request.id, response.requestId)
        assertEquals(request.name, response.name)
    }

    private inline fun <reified T : Any> T.serialize(): String = Gson().toJson(this)
}

package io.ktor.client.tests

import com.google.gson.Gson
import io.ktor.client.HttpClientFactory
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.Json
import io.ktor.client.get
import io.ktor.client.HttpClient
import io.ktor.client.pipeline.config
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.host.ApplicationHost
import io.ktor.host.embeddedServer
import io.ktor.http.ContentType
import io.ktor.jetty.Jetty
import io.ktor.pipeline.call
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test


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

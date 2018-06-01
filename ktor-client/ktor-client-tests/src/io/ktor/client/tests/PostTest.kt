package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import java.util.*
import kotlin.test.*

abstract class PostTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val prefix = "Hello, post"

    override val server = embeddedServer(Jetty, serverPort) {
        routing {
            post("/") {
                val content = call.receive<String>()
                assert(content.startsWith(prefix))
                call.respondText(content)
            }
        }
    }

    @Test
    fun postString() {
        postHelper(prefix)
    }

    @Test
    fun hugePost() {
        val builder = StringBuilder()
        val stringSize = 1024 * 1024 * 32
        val random = Random()

        while (builder.length < stringSize) {
            builder.append(random.nextInt(256).toChar())
        }

        postHelper("$prefix: $builder")
    }

    private fun postHelper(text: String) = runBlocking {
        val client = HttpClient(factory)

        val response = client.post<String>(port = serverPort, body = text)

        assertEquals(text, response)
        client.close()
    }

}

package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Assert.*
import java.util.*

open class PostTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val BODY_PREFIX = "Hello, post"

    override val server = embeddedServer(Jetty, serverPort) {
        routing {
            post("/") {
                val content = call.request.receiveContent().readText()
                assert(content.startsWith(BODY_PREFIX))
                call.respondText(content)
            }
        }
    }

    @Test
    fun postString() {
        postHelper(BODY_PREFIX)
    }

    @Test
    fun hugePost() {
        val builder = StringBuilder()
        val STRING_SIZE = 1024 * 1024 * 32
        val random = Random()

        while (builder.length < STRING_SIZE) {
            builder.append(random.nextInt(256).toChar())
        }

        postHelper("$BODY_PREFIX: $builder")
    }

    private fun postHelper(text: String) {
        val client = HttpClient(factory)

        val response = runBlocking {
            client.post<String>(port = serverPort, body = text)
        }

        assertEquals(text, response)
        client.close()
    }

}

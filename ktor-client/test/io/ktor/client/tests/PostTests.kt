package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.backend.jvm.*
import io.ktor.client.tests.utils.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.http.response.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import java.nio.charset.*
import java.util.*


class PostTests : TestWithKtor() {
    val BODY_PREFIX = "Hello, post"

    override val server = embeddedServer(Jetty, 8080) {
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
        val client = HttpClient(ApacheBackend)

        val response = runBlocking {
            client.post<String>(port = 8080, payload = text) {
                headers.contentType(ContentType.Text.Plain.withCharset(Charset.defaultCharset()))
            }
        }
        assert(response == text)

        client.close()
    }

}

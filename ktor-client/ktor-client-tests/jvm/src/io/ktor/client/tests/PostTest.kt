package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.junit.Assert.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
abstract class PostTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server = embeddedServer(Jetty, serverPort) {
        routing {
            post("/") {
                val content = call.receive<String>()
                call.respond(content)
            }
        }
    }

    @Test
    fun postString() {
        postHelper(makeString(777))
    }

    @Test
    fun hugePost() {
        postHelper(makeString(32 * 1024 * 1024))
    }

    private fun postHelper(text: String) = clientTest(factory) {
        test { client ->
            val response = client.post<String>(port = serverPort, body = text)
            assertEquals(text, response)
        }
    }

    @Test
    fun testWithPause() = clientTest(factory) {
        test { client ->
            val content = makeString(32 * 1024 * 1024)

            val response = client.post<String>(port = serverPort, body = object: OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8(content)
                    delay(1000)
                    channel.writeStringUtf8(content)
                    channel.close()
                }
            })

            assertEquals(content + content, response)
        }
    }

}

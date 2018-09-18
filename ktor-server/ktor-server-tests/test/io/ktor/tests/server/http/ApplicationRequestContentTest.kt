package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.io.*
import org.junit.Test
import kotlin.test.*

class ApplicationRequestContentTest {
    @Test
    fun testSimpleStringContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveText())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody("bodyContent")
            }
        }
    }

    @Test
    fun testStringValues() {
        withTestApplication {
            val values = parametersOf("a", "1")

            application.intercept(ApplicationCallPipeline.Call) {
                val actual = call.receiveParameters()
                assertEquals(values, actual)
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                val value = values.formUrlEncode()
                setBody(value)
            }
        }
    }

    @Test
    fun testStringValuesWithCharset() {
        withTestApplication {
            val values = parametersOf("a", "1")

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
                setBody(values.formUrlEncode())
            }
        }
    }

    @Test
    fun testInputStreamContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveStream().reader(Charsets.UTF_8).readText())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody("bodyContent")
            }
        }
    }

    @Test
    fun testCustomTransform() {
        withTestApplication {
            val value = IntList(listOf(1, 2, 3, 4))

            application.receivePipeline.intercept(ApplicationReceivePipeline.Transform) { query ->
                if (query.type != IntList::class) return@intercept
                val message = query.value as? ByteReadChannel ?: return@intercept

                val string = message.readRemaining().readText()
                val transformed = IntList.parse(string)
                proceedWith(ApplicationReceiveRequest(query.type, transformed))
            }

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(value, call.receive<IntList>())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody(value.toString())
            }
        }
    }

    @Test
    fun testFormUrlEncodedContent() {
        val values = parametersOf(
                "one" to listOf("1"),
                "two_space_three_and_four" to listOf("2 3 & 4")
        )
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(values.formUrlEncode())
            }
        }
    }

    @Test
    fun testReceiveUnsupportedTypeFailing(): Unit = withTestApplication {
        application.install(ContentNegotiation)

        application.routing {
            get("/") {
                val v = call.receive<IntList>()
                call.respondText(v.values.joinToString())
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals(415, call.response.status()?.value)
        }
    }

    @Test
    fun testReceiveUnsupportedTypeNotFailing(): Unit = withTestApplication {
        application.install(ContentNegotiation)

        application.routing {
            get("/") {
                val v = call.receiveOrNull<IntList>()
                call.respondText(v?.values?.joinToString() ?: "(none)")
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals(200, call.response.status()?.value)
        }
    }
}

data class IntList(val values: List<Int>) {
    override fun toString() = "$values"

    companion object {
        fun parse(text: String) = IntList(text.removeSurrounding("[", "]").split(",").map { it.trim().toInt() })
    }
}

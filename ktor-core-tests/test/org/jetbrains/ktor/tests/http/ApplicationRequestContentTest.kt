package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.io.*
import kotlin.test.*

class ApplicationRequestContentTest {
    @Test
    fun testSimpleStringContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.request.receive<String>())
            }

            handleRequest(HttpMethod.Get, "") {
                body = "bodyContent"
            }
        }
    }

    @Test
    fun testValuesMap() {
        withTestApplication {
            val values = valuesOf("a" to listOf("1"))

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.request.receive<ValuesMap>())
            }

            handleRequest(HttpMethod.Get, "") {
                method = HttpMethod.Post
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                body = values.formUrlEncode()
            }
        }
    }

    @Test
    fun testInputStreamContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.request.receive<InputStream>().reader(Charsets.UTF_8).readText())
            }

            handleRequest(HttpMethod.Get, "") {
                body = "bodyContent"
            }
        }
    }

    @Test
    fun testCustomTransform() {
        withTestApplication {
            val value = IntList(listOf(1, 2, 3, 4))

            application.receivePipeline.intercept(ApplicationReceivePipeline.Transform) { query ->
                if (query.type != IntList::class) return@intercept
                val message = query.value as? IncomingContent ?: return@intercept

                val string = message.readText()
                val transformed = IntList.parse(string)
                proceedWith(ApplicationReceiveRequest(query.type, transformed))
            }

            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(value, call.request.receive<IntList>())
            }

            handleRequest(HttpMethod.Get, "") {
                body = value.toString()
            }
        }
    }

    @Test
    fun testFormUrlEncodedContent() {
        val values = valuesOf(
                "one" to listOf("1"),
                "two_space_three_and_four" to listOf("2 3 & 4")
        )
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                assertEquals(values, call.request.receive<ValuesMap>())
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                body = values.formUrlEncode()
            }
        }
    }
}

data class IntList(val values: List<Int>) {
    override fun toString() = "$values"

    companion object {
        fun parse(text: String) = IntList(text.removeSurrounding("[", "]").split(",").map { it.trim().toInt() })
    }
}

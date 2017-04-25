package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.io.*
import kotlin.test.*

class ApplicationRequestContentTest {
    @Test
    fun testSimpleStringContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                assertEquals("bodyContent", call.request.receive<String>())
            }

            handleRequest(HttpMethod.Get, "") {
                body = "bodyContent"
            }
        }
    }

    @Test
    fun testInputStreamContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                assertEquals("bodyContent", call.request.receive<InputStream>().reader(Charsets.UTF_8).readText())
            }

            handleRequest(HttpMethod.Get, "") {
                body = "bodyContent"
            }
        }
    }

    @Test
    fun testFormUrlEncodedContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                val parameters = call.request.receive<ValuesMap>()
                assertEquals("1", parameters["one"])
                assertEquals("2 3 & 4", parameters["two_space_three_and_four"])
            }

            handleRequest(HttpMethod.Get, "") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                body = valuesOf(
                        "one" to listOf("1"),
                        "two_space_three_and_four" to listOf("2 3 & 4")
                ).formUrlEncode()
            }
        }
    }
}
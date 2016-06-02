package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.io.*
import kotlin.test.*

class ApplicationRequestContentTest {
    @Test
    fun testSimpleStringContent() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                assertEquals("bodyContent", call.request.content.get<String>())
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
                assertEquals("bodyContent", call.request.content.get<InputStream>().reader(Charsets.UTF_8).readText())
            }

            handleRequest(HttpMethod.Get, "") {
                body = "bodyContent"
            }
        }
    }
}
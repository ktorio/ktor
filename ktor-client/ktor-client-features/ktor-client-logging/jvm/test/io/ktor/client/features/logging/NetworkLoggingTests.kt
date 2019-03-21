package io.ktor.client.features.logging

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.*
import kotlin.test.*

@Ignore("Log structure is engine dependent")
class NetworkLoggingTest : TestWithKtor() {
    val content = "Response data"

    override val server: ApplicationEngine = embeddedServer(Jetty, port = serverPort) {
        routing {
            get("/") {
                call.respondText("home page")
            }
            post("/") {
                assertEquals(content, call.receiveText())
                call.respondText("/", status = HttpStatusCode.Created)
            }
            get("/301") {
                call.respondRedirect("/")
            }
        }
    }


    @Test
    fun loggingLevelTest() = clientsTest {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.ALL
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.HEADERS
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.BODY
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.INFO
        )

        checkLog("", HttpMethod.Get, "/", null, LogLevel.NONE)
    }

    @Test
    fun logPostBodyTest() = clientsTest {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=POST)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: text/plain; charset=UTF-8
BODY START
Response data
BODY END
RESPONSE: 201 Created
METHOD: HttpMethod(value=POST)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 1
BODY Content-Type: text/plain; charset=utf-8
BODY START
/
BODY END

        """.trimIndent(), HttpMethod.Post, "/", content, LogLevel.ALL
        )
    }

    @Test
    fun logRedirectTest() = clientsTest {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/301
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 302 Found
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/301
COMMON HEADERS
-> Location: /
-> Content-Length: 0
BODY Content-Type: null
BODY START

BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/301", null, LogLevel.ALL
        )
    }

    @Test
    fun customServerTest(): Unit = clientsTest {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.HEADERS
            }
        }

        test { client ->
            client.get<String>("http://google.com")
        }
    }

    private fun TestClientBuilder<*>.checkLog(
        expected: String,
        requestMethod: HttpMethod,
        path: String, body: String?, logLevel: LogLevel
    ) {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                logger = testLogger
                level = logLevel
            }
        }

        test { client ->
            val response = client.request<HttpResponse> {
                method = requestMethod

                url {
                    encodedPath = path
                }

                port = serverPort

                body?.let { this@request.body = body }
            }

            response.readText()
            response.close()
            response.coroutineContext[Job]?.join()

            assertEquals(expected, testLogger.dump())
        }
    }
}

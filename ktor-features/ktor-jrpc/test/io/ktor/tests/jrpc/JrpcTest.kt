package io.ktor.tests.jrpc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jrpc.JrpcSupport
import io.ktor.jrpc.extensions.jrpc
import io.ktor.jrpc.model.JrpcError
import io.ktor.jrpc.model.JrpcException
import io.ktor.jrpc.model.JrpcRequest
import io.ktor.jrpc.model.JrpcResponse
import io.ktor.response.header
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.tests.jrpc.handlers.rootHandler
import io.ktor.tests.jrpc.handlers.startTimeString
import io.ktor.tests.jrpc.model.User
import io.ktor.tests.jrpc.routers.*
import org.junit.Test
import java.text.DateFormat
import kotlin.test.assertEquals

class JrpcTest {

    /**
     * This test check basic denying for invalid requests and one proper request without params
     */
    @Test
    fun testBasicJrpcConditions() = withTestApplication {

        val contextPath = "ktor-jrpc-test"
        val jrpcPathDslConfigured = "/json-rpc"
        val methodPing = "ping"
        val methodHello = "hello"
        val contentTypeJson = ContentType.Application.Json.toString()
        val gson = Gson()
        val requestId = 1L

        application.run {
            install(DefaultHeaders)
            install(JrpcSupport) {
                gson {
                    setDateFormat(DateFormat.LONG)
                }
            }
            routing {

                route(contextPath) {

                    get("/") { rootHandler() }

                    jrpc(jrpcPathDslConfigured) {

                        method<Unit>(methodPing) {
                            "pong"
                        }

                        method<User>(methodHello) { user ->
                            val token = call.request.headers["Session-Token"]
                            "Hello, ${user.name}${if (token == null) " you must authorize" else " your token is $token"}"
                        }
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/$contextPath/")
                .response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(startTimeString, response.content)
        }

        val jrpcFullPathDslConfigured = "/$contextPath/$jrpcPathDslConfigured"

        assertEquals(HttpStatusCode.MethodNotAllowed, handleRequest(HttpMethod.Get, jrpcFullPathDslConfigured).response.status())
        assertEquals(HttpStatusCode.UnsupportedMediaType, handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured).response.status())

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody("definitely not a JRPC request in body")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(null, jrpcResponse?.id)
            assertEquals(JrpcError.CODE_PARSE_ERROR, jrpcResponse?.error?.code)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, "piu")))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(JrpcError.CODE_METHOD_NOT_FOUND, jrpcResponse?.error?.code)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodHello)))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(JrpcError.CODE_INVALID_PARAMS, jrpcResponse?.error?.code)
            assertEquals("Params is null while handler func is not Unit", jrpcResponse?.error?.message)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodHello, JsonArray())))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(JrpcError.CODE_INVALID_PARAMS, jrpcResponse?.error?.code)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodPing)))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals("pong", jrpcResponse?.result)
        }


    }

    /**
     * This test check call which need to convert params
     */
    @Test
    fun testCallWithParams() = withTestApplication {

        val contextPath = "ktor-jrpc-test"
        val jrpcPathDslConfigured = "/json-rpc"
        val methodHello = "hello"
        val contentTypeJson = ContentType.Application.Json.toString()
        val gson = Gson()
        val testUser = "Alex"
        val testJsonElement = JsonObject().apply { addProperty("name", testUser) }
        val testHeader = "Session-Token"
        val testToken = "100%_TOKEN_TRUST_ME"
        val requestId = 1L

        application.run {
            install(DefaultHeaders)
            install(JrpcSupport) {
                gson {
                    setDateFormat(DateFormat.LONG)
//                    registerTypeAdapter()
                }
            }
            routing {

                route(contextPath) {

                    jrpc(jrpcPathDslConfigured) {

                        method<User>(methodHello) { user ->
                            val token = call.request.headers["Session-Token"]
                            "Hello, ${user.name}${if (token == null) " you must authorize" else " your token is $token"}"
                        }
                    }
                }
            }
        }

        val jrpcFullPathDslConfigured = "/$contextPath/$jrpcPathDslConfigured"

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodHello, JsonObject())))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            //this will be invalid when GSON will not serialize nulls
            assertEquals(null, jrpcResponse?.error)
            assertEquals("Hello, null you must authorize", jrpcResponse?.result)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodHello, testJsonElement)))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals("Hello, $testUser you must authorize", jrpcResponse?.result)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            addHeader(testHeader, testToken)
            setBody(gson.toJson(JrpcRequest(requestId, methodHello, testJsonElement)))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals("Hello, $testUser your token is $testToken", jrpcResponse?.result)
        }
    }

    /**
     * This test check beforeJrpc and afterJrpc handlers
     */
    @Test
    fun testBeforeAfterJrpc() = withTestApplication {

        val contextPath = "ktor-jrpc-test"
        val jrpcPathDslConfigured = "/json-rpc"
        val jrpcPathObjectConfigured = "/json-rpc-echo"
        val contentTypeJson = ContentType.Application.Json.toString()
        val gson = Gson()
        val testHeaderBefore = "beforeJrpc"
        val testHeaderAfter = "afterJrpc"
        val testPropertyKaboom = "kaboom"
        val methodCheckBeforeAfter = "checkBeforeAfter"
        val requestId = 1L
        val testErrorCode = -32001

        application.run {
            install(DefaultHeaders)
            install(JrpcSupport) {
                gson {
                    setDateFormat(DateFormat.LONG)
                }
            }
            routing {

                route(contextPath) {

                    jrpc(jrpcPathDslConfigured) {

                        method<Map<String, String>>(methodCheckBeforeAfter) { params ->
                            if (testPropertyKaboom in params) {
                                throw JrpcException("Kaboom requested", testErrorCode)
                            } else {
                                "ok"
                            }
                        }

                        beforeJrpc { jrpcRequest ->
                            call.response.header(testHeaderBefore, jrpcRequest.method)
                            null
                        }

                        afterJrpc { jrpcRequest, jrpcResponse ->
                            call.response.header(testHeaderAfter, jrpcRequest.method)
                            jrpcResponse
                        }
                    }

                    jrpc(jrpcPathObjectConfigured, echoRouter)
                }
            }
        }

        val jrpcFullPathDslConfigured = "/$contextPath/$jrpcPathDslConfigured"

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodCheckBeforeAfter, JsonObject())))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals("ok", jrpcResponse?.result)
            assertEquals(methodCheckBeforeAfter, response.headers[testHeaderBefore])
            assertEquals(methodCheckBeforeAfter, response.headers[testHeaderAfter])
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(
                    JrpcRequest(requestId, methodCheckBeforeAfter, JsonObject()
                            .apply { addProperty(testPropertyKaboom, true) })
            ))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.result)
            assertEquals(testErrorCode, jrpcResponse?.error?.code)
            assertEquals(methodCheckBeforeAfter, response.headers[testHeaderBefore])
            assertEquals(methodCheckBeforeAfter, response.headers[testHeaderAfter])
        }

        handleRequest(HttpMethod.Post, jrpcFullPathDslConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody("not a valid JSON body")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(null, jrpcResponse?.id)
            assertEquals(JrpcError.CODE_PARSE_ERROR, jrpcResponse?.error?.code)
            assertEquals(null, response.headers[testHeaderBefore])
            assertEquals(null, response.headers[testHeaderAfter])
        }
    }

    /**
     * This test check object configured router
     */
    @Test
    fun testObjectConfiguredRouter() = withTestApplication {

        val contextPath = "ktor-jrpc-test"
        val jrpcPathObjectConfigured = "json-rpc-echo"
        val contentTypeJson = ContentType.Application.Json.toString()
        val gson = Gson()
        val requestId = 1L

        application.run {
            install(DefaultHeaders)
            install(JrpcSupport) {
                gson {
                    setDateFormat(DateFormat.LONG)
                }
            }
            routing {

                route(contextPath) {

                    jrpc(jrpcPathObjectConfigured, echoRouter)
                }
            }
        }

        val jrpcFullPathObjectConfigured = "/$contextPath/$jrpcPathObjectConfigured"

        handleRequest(HttpMethod.Post, jrpcFullPathObjectConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodEcho, JsonArray())))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals(emptyResponse, jrpcResponse?.result)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathObjectConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(
                    JrpcRequest(requestId, methodEcho, JsonArray(2).apply {
                        add("Hello")
                        add("World")
                    })
            ))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals("Hello${System.lineSeparator()}World", jrpcResponse?.result)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathObjectConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody("not a valid JSON body")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(JrpcError.CODE_PARSE_ERROR, jrpcResponse?.error?.code)
        }

        handleRequest(HttpMethod.Post, jrpcFullPathObjectConfigured) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(
                    JrpcRequest(requestId, methodEcho, JsonArray(1).apply {
                        add(secretRequest)
                    })
            ))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals(secretResponse, jrpcResponse?.result)
        }
    }

    /**
     * This test check if different routers has methods with same names
     */
    @Test
    fun testTwoJrpcRoutersWithSameNamedMethod() = withTestApplication {

        val jrpcPath1 = "/json-rpc1"
        val jrpcPath2 = "/json-rpc2"
        val methodName1 = "test1"
        val methodResult1 = "ok1"
        val methodData2 = listOf("a", "b")
        val methodResult2 = methodData2.size
        val contentTypeJson = ContentType.Application.Json.toString()
        val gson = Gson()
        val requestId = 1L

        application.run {
            install(DefaultHeaders)
            install(JrpcSupport) {
                gson {
                    setDateFormat(DateFormat.LONG)
                }
            }
            routing {

                jrpc(jrpcPath1) {
                    method<Unit>(methodName1) {
                        methodResult1
                    }
                }

                jrpc(jrpcPath2) {
                    method<List<String>>(methodName1) { list ->
                        list.size
                    }
                }
            }
        }

        handleRequest(HttpMethod.Post, jrpcPath1) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodName1)))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals(methodResult1, jrpcResponse?.result)
        }

        handleRequest(HttpMethod.Post, jrpcPath2) {
            addHeader(HttpHeaders.ContentType, contentTypeJson)
            setBody(gson.toJson(JrpcRequest(requestId, methodName1, JsonArray().apply {
                methodData2.forEach { add(it) }
            })))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            val jrpcResponse = gson.fromJson(response.content, JrpcResponse::class.java)
            assertEquals(requestId, jrpcResponse?.id)
            assertEquals(null, jrpcResponse?.error)
            assertEquals(methodResult2, jrpcResponse?.result?.toString()?.toFloat()?.toInt())
        }
    }
}


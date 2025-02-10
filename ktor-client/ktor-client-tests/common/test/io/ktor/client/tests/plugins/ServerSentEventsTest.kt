/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.sse.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ServerSentEventsTest : ClientLoader() {

    @Test
    fun testExceptionIfSseIsNotInstalled() = runTest {
        val client = HttpClient()
        assertFailsWith<IllegalStateException> {
            client.serverSentEventsSession {}
        }.apply {
            assertContains(message!!, SSE.key.name)
        }
        assertFailsWith<IllegalStateException> {
            client.serverSentEvents {}
        }.apply {
            assertContains(message!!, SSE.key.name)
        }
    }

    @Test
    fun normalRequestsWorkWithSSEInstalled() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.post("$TEST_SERVER/content/echo") {
                setBody("Hello")
            }.apply {
                assertEquals("Hello", bodyAsText())
            }
        }
    }

    @Test
    fun testSseSession() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello")
            session.incoming.single().apply {
                assertEquals("0", id)
                assertEquals("hello 0", event)
                val lines = data?.lines() ?: emptyList()
                assertEquals(2, lines.size)
                assertEquals("hello", lines[0])
                assertEquals("from server", lines[1])
            }
            session.cancel()
        }
    }

    @Test
    fun testParallelSseSessions() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            coroutineScope {
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=100")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        val lines = it.data?.lines() ?: emptyList()
                        assertEquals(2, lines.size)
                        assertEquals("hello", lines[0])
                        assertEquals("from server", lines[1])
                        size++
                    }
                    assertEquals(100, size)
                    session.cancel()
                }
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=50")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        val lines = it.data?.lines() ?: emptyList()
                        assertEquals(2, lines.size)
                        assertEquals("hello", lines[0])
                        assertEquals("from server", lines[1])
                        size++
                    }
                    assertEquals(50, size)
                    session.cancel()
                }
            }
        }
    }

    @Test
    fun testSseSessionUnknownHostError() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            assertFailsWith<SSEClientException> {
                client.serverSentEventsSession("http://unknown_host")
            }.apply {
                assertNotNull(cause)
            }
        }
    }

    @Test
    fun testExceptionDuringSSESession() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            assertFailsWith<SSEClientException> {
                client.serverSentEvents("$TEST_SERVER/sse/hello") { error("error") }
            }.apply {
                assertTrue { cause is IllegalStateException }
                assertEquals("error", message)
                assertEquals(HttpStatusCode.OK, response?.status)
            }
        }
    }

    @Test
    fun testCancellationExceptionSse() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            coroutineScope {
                val job: Job
                suspendCoroutine { cont ->
                    job = launch {
                        client.serverSentEvents("$TEST_SERVER/sse/hello") {
                            cont.resume(Unit)
                            awaitCancellation()
                        }
                    }
                }
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun testNoCommentsByDefault() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    assertEquals("${i * 2 + 1}", it.data)
                    size++
                }
                assertEquals(50, size)
            }
        }
    }

    @Test
    fun testShowComments() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                showCommentEvents()
            }
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    if (i % 2 == 0) {
                        assertEquals("$i", it.comments)
                    } else {
                        assertEquals("$i", it.data)
                    }
                    size++
                }
                assertEquals(100, size)
            }
        }
    }

    @Test
    fun testDifferentConfigs() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                showCommentEvents()
            }
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50", showCommentEvents = false) {
                var size = 0
                incoming.collectIndexed { i, it ->
                    assertEquals("${2 * i + 1}", it.data)
                    size++
                }
                assertEquals(50, size)
            }

            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                var size = 0
                incoming.collectIndexed { i, it ->
                    if (i % 2 == 0) {
                        assertEquals("$i", it.comments)
                    } else {
                        assertEquals("$i", it.data)
                    }
                    size++
                }
                assertEquals(100, size)
            }
        }
    }

    @Test
    fun testRequestTimeoutIsNotApplied() = clientTests {
        config {
            install(SSE)

            install(HttpTimeout) {
                requestTimeoutMillis = 10
            }
        }

        test { client ->
            client.sse("$TEST_SERVER/sse/hello?delay=20") {
                val result = incoming.single()
                assertEquals("hello 0", result.event)
            }
        }
    }

    @Test
    fun testWithAuthPlugin() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }

            install(SSE)
        }

        test { client ->
            client.sse("$TEST_SERVER/sse/auth") {
                val result = incoming.single()
                assertEquals("hello after refresh", result.data)
            }
        }
    }

    @Test
    fun testSseExceptionWhenResponseStatusIsNot200() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            assertFailsWith<SSEClientException> {
                client.sse("$TEST_SERVER/sse/404") {}
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response?.status)
                assertEquals("Expected status code 200 but was 404", message)
            }
        }
    }

    @Test
    fun testSseExceptionWhenResponseContentTypeNotRight() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            assertFailsWith<SSEClientException> {
                client.sse("$TEST_SERVER/sse/content-type-text-plain") {}
            }.apply {
                assertEquals(HttpStatusCode.OK, response?.status)
                assertEquals(ContentType.Text.Plain, response?.contentType())
                assertEquals("Expected Content-Type text/event-stream but was text/plain", message)
            }
        }
    }

    @Test
    fun testContentTypeWithCharset() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.sse("$TEST_SERVER/sse/content_type_with_charset") {
                assertEquals(ContentType.Text.EventStream.withCharset(Charsets.UTF_8), call.response.contentType())

                incoming.single().apply {
                    assertEquals("0", id)
                    assertEquals("hello 0", event)
                    val lines = data?.lines() ?: emptyList()
                    assertEquals(2, lines.size)
                    assertEquals("hello", lines[0])
                    assertEquals("from server", lines[1])
                }
            }
        }
    }

    // Android, Darwin and Js engines don't support request body in GET request
    // SSE in OkHttp and Curl doesn't send a request body for GET request
    @Test
    fun testRequestBody() = clientTests(except("Android", "Darwin", "DarwinLegacy", "Js", "OkHttp", "Curl")) {
        config {
            install(SSE)
        }

        val body = "hello"
        val contentType = ContentType.Text.Plain
        test { client ->
            client.sse({
                url("$TEST_SERVER/sse/echo")
                setBody(body)
                contentType(contentType)
            }) {
                assertEquals(contentType, call.request.contentType()?.withoutParameters())
                assertEquals(body, incoming.single().data)
            }
        }
    }

    @Test
    fun testErrorForProtocolUpgradeRequestBody() = clientTests(except("OkHttp")) {
        config {
            install(SSE)
        }

        val body = object : OutgoingContent.ProtocolUpgrade() {

            override suspend fun upgrade(
                input: ByteReadChannel,
                output: ByteWriteChannel,
                engineContext: CoroutineContext,
                userContext: CoroutineContext
            ): Job {
                output.flushAndClose()
                return Job()
            }
        }
        test { client ->
            assertFailsWith<SSEClientException> {
                client.sse({
                    url("$TEST_SERVER/sse/echo")
                    setBody(body)
                }) {}
            }
        }
    }

    @Test
    fun testPostRequest() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.sse({
                url("$TEST_SERVER/sse")
                method = HttpMethod.Post
            }) {
                incoming.single().apply {
                    assertEquals("Hello", data)
                }
            }
        }
    }

    @Test
    fun testSseWithLogging() = clientTests {
        config {
            install(SSE)
            install(Logging) {
                level = LogLevel.ALL
            }
        }

        test { client ->
            client.sse({
                url("$TEST_SERVER/sse")
                method = HttpMethod.Post
            }) {
                incoming.single().apply {
                    assertEquals("Hello", data)
                }
            }
        }
    }

    class Person(val name: String)
    class Data(val value: String)

    @Test
    fun testDeserializer() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            val count = 10
            var size = 0
            client.sse(
                {
                    url("$TEST_SERVER/sse/person")
                    parameter("times", count)
                },
                deserialize = { _, it -> Person(it) }
            ) {
                incoming.collectIndexed { i, event ->
                    val person = deserialize<Person>(event)
                    assertEquals("Name $i", person?.name)
                    assertEquals("$i", event.id)
                    size++
                }
            }
            assertEquals(count, size)
        }
    }

    @Test
    fun testExceptionIfWrongDeserializerProvided() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            assertFailsWith<SSEClientException> {
                client.sse({ url("$TEST_SERVER/sse/person") }, { _, it -> Data(it) }) {
                    incoming.single().apply {
                        val data = deserialize<Person>(data)
                        assertEquals("Name 0", data?.name)
                    }
                }
            }
        }
    }

    class Person1(val name: String)
    class Person2(val middleName: String)

    @Test
    fun testDifferentDeserializers() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.sse({ url("$TEST_SERVER/sse/person") }, deserialize = { _, str -> Person1(str) }) {
                incoming.single().apply {
                    assertEquals("Name 0", deserialize<Person1>(data)?.name)
                }
            }
            client.sse({ url("$TEST_SERVER/sse/person") }, deserialize = { _, str -> Person2(str) }) {
                incoming.single().apply {
                    assertEquals("Name 0", deserialize<Person2>(data)?.middleName)
                }
            }
        }
    }

    @Serializable
    data class Customer(val id: Int, val firstName: String, val lastName: String)

    @Serializable
    data class Product(val name: String, val price: Int)

    @Test
    fun testJsonDeserializer() = clientTests {
        config {
            install(SSE)
        }

        test { client ->
            client.sse({
                url("$TEST_SERVER/sse/json")
            }, deserialize = { typeInfo, jsonString ->
                val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                Json.decodeFromString(serializer, jsonString) ?: Exception()
            }) {
                var firstIsCustomer = true
                incoming.collect { event: TypedServerSentEvent<String> ->
                    if (firstIsCustomer) {
                        val customer = deserialize<Customer>(event.data)
                        assertEquals(1, customer?.id)
                        assertEquals("Jet", customer?.firstName)
                        assertEquals("Brains", customer?.lastName)
                        firstIsCustomer = false
                    } else {
                        val product = deserialize<Product>(event.data)
                        assertEquals("Milk", product?.name)
                        assertEquals(100, product?.price)
                        cancel()
                    }
                }
            }
        }
    }

    @Test
    fun testReconnection() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                maxReconnectionAttempts = 1
                reconnectionTime = 2.seconds
            }
        }

        test { client ->
            val events = mutableListOf<ServerSentEvent>()

            val time = measureTime {
                client.sse("$TEST_SERVER/sse/reconnection?count=5") {
                    incoming.take(10).collect {
                        events.add(it)
                    }
                }
            }

            events.forEachIndexed { index, event ->
                assertEquals(index + 1, event.id?.toInt())
            }
            assertTrue { time > 2.seconds }
        }
    }

    @Test
    fun testClientExceptionDuringSSESession() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                maxReconnectionAttempts = 1
            }
        }

        test { client ->
            val events = mutableListOf<ServerSentEvent>()
            var count = 0

            assertFailsWith<IllegalStateException> {
                client.sse("$TEST_SERVER/sse/reconnection?count=5", reconnectionTime = 1.seconds) {
                    incoming.collect {
                        events.add(it)
                        count++

                        if (count == 7) {
                            throw IllegalStateException("Client exception")
                        }
                    }
                }
            }

            assertTrue(events.size == 7)
            events.forEachIndexed { index, event ->
                assertEquals(index + 1, event.id?.toInt())
            }
        }
    }

    @Test
    fun testServerExceptionDuringSSESession() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                reconnectionTime = 100.milliseconds
                maxReconnectionAttempts = 1
            }
        }

        test { client ->
            val events = mutableListOf<ServerSentEvent>()

            assertFailsWith<SSEClientException> {
                client.sse("$TEST_SERVER/sse/exception-on-reconnection?count=5") {
                    incoming.collect {
                        events.add(it)
                    }
                }
            }.apply {
                assertEquals("Expected status code 200 but was 500", message)
            }

            assertEquals(5, events.size)
            events.forEachIndexed { index, event ->
                assertEquals("$index", event.id)
            }
        }
    }

    @Test
    fun testSeveralReconnections() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                reconnectionTime = 500.milliseconds
                maxReconnectionAttempts = 2
            }
        }

        test { client ->
            val events = mutableListOf<ServerSentEvent>()
            var count = 0

            val time = measureTime {
                client.sse("$TEST_SERVER/sse/reconnection?count=5") {
                    incoming.collect {
                        events.add(it)
                        count++
                        if (count == 15) {
                            cancel()
                        }
                    }
                }
            }

            assertEquals(15, events.size)
            events.forEachIndexed { index, event ->
                assertEquals(index + 1, event.id?.toInt())
            }
            assertTrue { 1.seconds < time && time < 2.seconds }
        }
    }

    @Test
    fun testMaxRetries() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                reconnectionTime = 500.milliseconds
                maxReconnectionAttempts = 4
            }
        }

        test { client ->
            val events = mutableListOf<ServerSentEvent>()
            var count = 0

            val time = measureTime {
                client.sse("$TEST_SERVER/sse/exception-on-reconnection?count=5&count-of-reconnections=4") {
                    incoming.collect {
                        events.add(it)
                        count++
                        if (count == 10) {
                            cancel()
                        }
                    }
                }
            }

            assertEquals(10, events.size)
            events.forEachIndexed { index, event ->
                assertEquals(index % 5, event.id?.toInt())
            }
            assertTrue { 2.seconds < time && time < 3.seconds }
        }
    }

    @Test
    fun testNoContent() = clientTests(except("OkHttp")) {
        config {
            install(SSE) {
                maxReconnectionAttempts = 1
                reconnectionTime = 0.milliseconds
            }
        }

        test { client ->

            client.sse("$TEST_SERVER/sse/no-content") {
                assertEquals(HttpStatusCode.NoContent, call.response.status)
                assertEquals(0, incoming.toList().size)
            }

            val events = mutableListOf<ServerSentEvent>()
            client.sse("$TEST_SERVER/sse/no-content-after-reconnection?count=10") {
                incoming.collect {
                    events.add(it)
                }
            }
            assertEquals(10, events.size)
            events.forEachIndexed { index, event ->
                assertEquals(index, event.id?.toInt())
            }
        }
    }
}

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TestApplicationTest {

    private val TestPlugin = createApplicationPlugin("test_plugin") {
        onCall { call ->
            if (!call.request.headers.contains("request_header")) {
                throw BadRequestException("Error")
            }
            call.response.header("response_header", call.request.headers["request_header"]!!)
        }
    }
    private val testApplication = TestApplication {
        install(TestPlugin)
        routing {
            get("a") {
                call.respond("OK")
            }
        }
    }

    private object TestClientPlugin : HttpClientPlugin<Unit, TestClientPlugin> {
        override val key: AttributeKey<TestClientPlugin> = AttributeKey("testClientPlugin")

        override fun prepare(block: Unit.() -> Unit) = TestClientPlugin

        override fun install(plugin: TestClientPlugin, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                context.headers.append("request_header", "value_1")
            }
        }
    }

    private val testClient = testApplication.createClient {
        install(TestClientPlugin)
    }

    @Test
    fun testTopLevel() = runTest {
        val response = testClient.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testApplicationBlock() = testApplication {
        application {
            install(TestPlugin)
            routing {
                get("a") {
                    call.respond("OK")
                }
            }
        }

        val client = createClient { install(TestClientPlugin) }
        val response = client.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test // KTOR-7682
    fun testApplicationBlockStartBeforeClientCall() = runTest(timeout = 5.seconds) {
        val application = TestApplication {
            serverConfig {
                parentCoroutineContext = coroutineContext
            }
        }

        application.start()
        application.client.get("/")
        application.stop()
    }

    @Test
    fun testBridge() = testApplication {
        install(TestPlugin)
        routing {
            get("a") {
                call.respond("OK")
            }
        }

        val client = createClient { install(TestClientPlugin) }
        val response = client.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testExternalServices() = testApplication {
        externalServices {
            hosts("http://www.google.com:123", "https://google.com") {
                routing {
                    get { call.respond("EXTERNAL OK") }
                }
            }
        }

        routing {
            get { call.respond("OK") }
        }

        val internal = client.get("/")
        assertEquals("OK", internal.bodyAsText())

        val external1 = client.get("//www.google.com:123")
        assertEquals("EXTERNAL OK", external1.bodyAsText())
        val external2 = client.get("https://google.com")
        assertEquals("EXTERNAL OK", external2.bodyAsText())
        assertFailsWith<InvalidTestRequestException> { client.get("//google.com:123") }
        assertFailsWith<InvalidTestRequestException> { client.get("//google.com") }
        assertFailsWith<InvalidTestRequestException> { client.get("https://www.google.com") }
    }

    @Test
    fun testCanAccessClient() = testApplication {
        class TestPluginConfig {
            lateinit var pluginClient: HttpClient
        }

        val TestPlugin = createApplicationPlugin("test", ::TestPluginConfig) {
            onCall {
                val externalValue = pluginConfig.pluginClient.get("https://test.com").bodyAsText()
                it.response.headers.append("test", externalValue)
            }
        }
        install(TestPlugin) {
            pluginClient = client
        }
        externalServices {
            hosts("https://test.com") {
                routing { get { call.respond("TEST_VALUE") } }
            }
        }
        routing {
            get {
                call.respond("OK")
            }
        }

        val response = client.get("/")
        assertEquals("OK", response.bodyAsText())
        assertEquals("TEST_VALUE", response.headers["test"])
    }

    @Test
    fun testingSchema() = testApplication {
        routing {
            get("/echo") {
                call.respondText(call.request.local.scheme)
            }
        }

        assertEquals("http", client.get("/echo").bodyAsText())
        assertEquals("https", client.get("https://localhost/echo").bodyAsText())
    }

    @Test
    fun testManualStart() = testApplication {
        var externalServicesRoutingCalled = false
        var routingCalled = false
        var applicationCalled = false
        var applicationStarted = false
        externalServices {
            hosts("https://test.com") {
                routing {
                    externalServicesRoutingCalled = true
                }
            }
        }
        routing {
            routingCalled = true
        }
        application {
            applicationCalled = true
            monitor.subscribe(ApplicationStarted) {
                applicationStarted = true
            }
        }

        startApplication()

        assertEquals(true, routingCalled)
        assertEquals(true, applicationCalled)
        assertEquals(true, applicationStarted)
        assertEquals(true, externalServicesRoutingCalled)
    }

    @Test
    fun testManualStartAndClientCallAfter() = testApplication {
        externalServices {
            hosts("https://test.com") {
                routing {
                    get {
                        call.respond("OK")
                    }
                }
            }
        }
        routing {
            get {
                call.respond(this@testApplication.client.get("https://test.com").bodyAsText())
            }
        }

        startApplication()

        assertEquals("OK", client.get("/").bodyAsText())
    }

    @Test
    fun testClientWithTimeout() = testApplication {
        val client = createClient {
            install(HttpTimeout)
        }
        externalServices {
            hosts("http://fake.site.io") {
                routing {
                    get("/toto") {
                        call.respond("OK")
                    }
                }
            }
        }
        routing {
            get("/") {
                val response = client.get("http://fake.site.io/toto") {
                    timeout {
                        requestTimeoutMillis = 100
                    }
                }.bodyAsText()
                call.respondText(response)
            }
        }

        assertEquals("OK", client.get("/").bodyAsText())
    }

    @Test
    fun testMultipleParallelRequests() = testApplication {
        routing {
            get("/") {
                call.respondText("OK")
            }
        }

        coroutineScope {
            val jobs = (1..100).map {
                async {
                    client.get("/").apply {
                        assertEquals("OK", bodyAsText())
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    @Test
    fun testRequestRunningInParallel() = testApplication {
        routing {
            post("/") {
                val text = call.receiveText()
                call.respondText(text)
            }
        }

        coroutineScope {
            val secondRequestStarted = CompletableDeferred<Unit>()
            val request1 = async {
                client.post("/") {
                    setBody(object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("OK")
                            secondRequestStarted.await()
                            channel.flush()
                        }
                    })
                }.apply {
                    assertEquals("OK", bodyAsText())
                }
            }
            val request2 = async {
                client.preparePost("/") {
                    setBody("OK")
                }.execute {
                    secondRequestStarted.complete(Unit)
                    assertEquals("OK", it.bodyAsText())
                }
            }
            request1.await()
            request2.await()
        }
    }

    @Test
    fun testInternalServerError() = testApplication {
        routing {
            get("/boom") {
                throw IllegalStateException("error")
            }
        }

        assertEquals(HttpStatusCode.InternalServerError, client.get("/boom").status)
    }

    @Test
    fun testExceptionRespondsWith500IfFlagSet() = testApplication {
        environment {
            config = MapApplicationConfig(CONFIG_KEY_THROW_ON_EXCEPTION to "false")
        }
        routing {
            get("/boom") {
                throw IllegalStateException("error")
            }
        }

        val response = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun testConnectors() = testApplication {
        engine {
            connector {
                port = 8080
            }
            connector {
                port = 8081
            }
        }
        routing {
            port(8080) {
                get {
                    call.respond("8080")
                }
            }
            port(8081) {
                get {
                    call.respond("8081")
                }
            }
        }

        val response8080 = client.get {
            host = "0.0.0.0"
            port = 8080
        }
        assertEquals("8080", response8080.bodyAsText())

        val response8081 = client.get {
            host = "0.0.0.0"
            port = 8081
        }
        assertEquals("8081", response8081.bodyAsText())
    }

    @Test
    fun testStartupJobsCompletion() = testApplication {
        startApplication()
        withTimeoutOrNull(1000) {
            engine.application.coroutineContext.job.children.forEach { scope -> scope.join() }
        } ?: fail("Timed out waiting for child coroutines to finish")
    }

    @Test
    fun testCanPassCoroutineContextFromOutside() = testApplication(MyElement("test")) {
        assertEquals("test", coroutineContext[MyElement]!!.data)
        withContext(Dispatchers.Unconfined) {
            assertEquals("test", coroutineContext[MyElement]!!.data)
        }
        routing {
            get {
                call.respond(coroutineContext[MyElement]!!.data)
            }
        }
        assertEquals("test", client.get("/").bodyAsText())
    }

    private fun testSocketTimeoutRead(timeout: Long, expectException: Boolean) = testApplication {
        routing {
            get {
                call.respond(
                    HttpStatusCode.OK,
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeByteArray("Hello".toByteArray())
                            channel.flush()
                            delay(300)
                        }
                    }
                )
            }
        }

        val clientWithTimeout = createClient {
            install(HttpTimeout) {
                socketTimeoutMillis = timeout
            }
        }

        if (expectException) {
            assertFailsWith<SocketTimeoutException> {
                clientWithTimeout.get("/")
            }
        } else {
            clientWithTimeout.get("/").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun testSocketTimeoutReadElapsed() = testSocketTimeoutRead(100, true)

    @Test
    fun testSocketTimeoutReadNotElapsed() = testSocketTimeoutRead(1000, false)

    @Test
    fun configuration_file_is_not_loaded_automatically() = testApplication {
        application {
            assertNull(environment.config.propertyOrNull("test.property"))
        }
    }

    class MyElement(val data: String) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = MyElement

        companion object : CoroutineContext.Key<MyElement>
    }
}

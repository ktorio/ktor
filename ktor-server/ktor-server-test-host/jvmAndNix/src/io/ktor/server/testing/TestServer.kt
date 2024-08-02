/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.client.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A client attached to [TestServer].
 */
@KtorDsl
public interface ClientProvider {
    /**
     * Returns a client with the default configuration.
     * @see [testServer]
     */
    public val client: HttpClient

    /**
     * Creates a client with a custom configuration.
     * For example, to send JSON data in a test POST/PUT request, you can install the `ContentNegotiation` plugin:
     * ```kotlin
     * fun testPostCustomer() = testApplication {
     *     val client = createClient {
     *         install(ContentNegotiation) {
     *             json()
     *         }
     *     }
     * }
     * ```
     * @see [testServer]
     */
    @KtorDsl
    public fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient
}

@Deprecated(message = "Renamed to TestServer", replaceWith = ReplaceWith("TestServer"))
public typealias TestApplication = TestServer

/**
 * A configured instance of a test application running locally.
 * @see [testServer]
 */
public class TestServer internal constructor(
    private val builder: ServerTestBuilder
) : ClientProvider by builder {

    internal enum class State {
        Created, Starting, Started, Stopped
    }

    private val state = atomic(State.Created)

    internal val externalApplications by lazy { builder.externalServices.externalApplications }
    internal val server by lazy { builder.embeddedServer }
    private val applicationStarting by lazy { Job(server.engine.coroutineContext[Job]) }

    /**
     * Starts this [TestServer] instance.
     */
    public fun start() {
        if (state.compareAndSet(State.Created, State.Starting)) {
            try {
                builder.embeddedServer.start()
                builder.externalServices.externalApplications.values.forEach { it.start() }
            } finally {
                state.value = State.Started
                applicationStarting.complete()
            }
        }
        if (state.value == State.Starting) {
            runBlocking { applicationStarting.join() }
        }
    }

    /**
     * Stops this [TestServer] instance.
     */
    public fun stop() {
        state.value = State.Stopped
        builder.embeddedServer.stop()
        builder.externalServices.externalApplications.values.forEach { it.stop() }
        client.close()
    }
}

/**
 * Creates an instance of [TestServer] configured with the builder [block].
 * Make sure to call [TestServer.stop] after your tests.
 * @see [testServer]
 */
@KtorDsl
public fun TestServer(
    block: TestServerBuilder.() -> Unit
): TestServer {
    val builder = ServerTestBuilder()
    val testServer = TestServer(builder)
    builder.block()
    return testServer
}

/**
 * Registers mocks for external services.
 */
@KtorDsl
public class ExternalServicesBuilder internal constructor(private val testServerBuilder: TestServerBuilder) {

    private val externalApplicationBuilders = mutableMapOf<String, () -> TestServer>()
    internal val externalApplications: Map<String, TestServer> by lazy {
        externalApplicationBuilders.mapValues { it.value.invoke() }
    }

    /**
     * Registers a mock for an external service specified by [hosts] and configured with [block].
     * @see [testServer]
     */
    @KtorDsl
    public fun hosts(vararg hosts: String, block: Server.() -> Unit) {
        check(hosts.isNotEmpty()) { "hosts can not be empty" }

        hosts.forEach {
            val protocolWithAuthority = Url(it).protocolWithAuthority
            externalApplicationBuilders[protocolWithAuthority] = {
                TestServer {
                    environment(this@ExternalServicesBuilder.testServerBuilder.environmentBuilder)
                    serverModules.add(block)
                }
            }
        }
    }
}

@Deprecated(message = "Renamed to TestServerBuilder", replaceWith = ReplaceWith("TestServerBuilder"))
public typealias TestApplicationBuilder = TestServerBuilder

/**
 * A builder for [TestServer].
 */
@KtorDsl
public open class TestServerBuilder {

    private var built = false

    internal val externalServices = ExternalServicesBuilder(this)
    internal val serverModules = mutableListOf<Server.() -> Unit>()
    internal var engineConfig: TestServerEngine.Configuration.() -> Unit = {}
    internal var environmentBuilder: ServerEnvironmentBuilder.() -> Unit = {}
    internal var applicationProperties: ServerParametersBuilder.() -> Unit = {}
    internal val job = Job()

    internal val properties by lazy {
        built = true
        val environment = createTestEnvironment {
            val oldConfig = config
            this@TestServerBuilder.environmentBuilder(this)
            if (config == oldConfig) { // the user did not set config. load the default one
                config = MapServerConfig()
            }
        }
        serverParams(environment) {
            this@TestServerBuilder.serverModules.forEach { module(it) }
            parentCoroutineContext += this@TestServerBuilder.job
            watchPaths = emptyList()
            developmentMode = true
            this@TestServerBuilder.applicationProperties(this)
        }
    }

    internal val embeddedServer by lazy {
        EmbeddedServer(properties, TestEngine, engineConfig)
    }

    internal val engine by lazy {
        embeddedServer.engine
    }

    /**
     * Builds mocks for external services using [ExternalServicesBuilder].
     * @see [testServer]
     */
    @KtorDsl
    public fun externalServices(block: ExternalServicesBuilder.() -> Unit) {
        checkNotBuilt()
        externalServices.block()
    }

    /**
     * Adds a configuration block for the [TestServerEngine].
     * @see [testServer]
     */
    @KtorDsl
    public fun engine(block: TestServerEngine.Configuration.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = engineConfig
        engineConfig = { oldBuilder(); block() }
    }

    /**
     * Adds a configuration block for the [ServerParameters].
     * @see [testServer]
     */
    @KtorDsl
    public fun testApplicationProperties(block: ServerParametersBuilder.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = applicationProperties
        applicationProperties = { oldBuilder(); block() }
    }

    /**
     * Builds an environment using [block].
     * @see [testServer]
     */
    @KtorDsl
    public fun environment(block: ServerEnvironmentBuilder.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = environmentBuilder
        environmentBuilder = { oldBuilder(); block() }
    }

    /**
     * Adds a module to [TestServer].
     * @see [testServer]
     */
    @KtorDsl
    public fun application(block: Server.() -> Unit) {
        checkNotBuilt()
        serverModules.add(block)
    }

    /**
     * Installs a [plugin] into [TestServer]
     */
    @Suppress("UNCHECKED_CAST")
    @KtorDsl
    public fun <P : Pipeline<*, PipelineCall>, B : Any, F : Any> install(
        plugin: Plugin<P, B, F>,
        configure: B.() -> Unit = {}
    ) {
        checkNotBuilt()
        serverModules.add { install(plugin as Plugin<ServerCallPipeline, B, F>, configure) }
    }

    /**
     * Installs routing into [TestServer]
     */
    @KtorDsl
    public fun routing(configuration: Route.() -> Unit) {
        checkNotBuilt()
        serverModules.add { routing(configuration) }
    }

    private fun checkNotBuilt() {
        check(!built) {
            "The test application has already been built. Make sure you configure the application " +
                "before accessing the client for the first time."
        }
    }
}

@Deprecated(message = "Renamed to ServerTestBuilder", replaceWith = ReplaceWith("ServerTestBuilder"))
public typealias ApplicationTestBuilder = ServerTestBuilder

/**
 * A builder for a test that uses [TestServer].
 */
@KtorDsl
public class ServerTestBuilder : TestServerBuilder(), ClientProvider {

    override val client: HttpClient by lazy { createClient { } }

    internal val application: TestServer by lazy { TestServer(this) }

    /**
     * Starts instance of [TestServer].
     * Usually, users do not need to call this method because application will start on the first client call.
     * But it's still useful when you need to test your application lifecycle events.
     *
     * After calling this method, no modification of the application is allowed.
     */
    public fun startApplication() {
        application.start()
    }

    @KtorDsl
    override fun createClient(
        block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
    ): HttpClient = HttpClient(DelegatingTestClientEngine) {
        engine {
            parentJob = this@ServerTestBuilder.job
            testServerProvder = this@ServerTestBuilder::application
        }
        block()
    }
}

@Deprecated(message = "Renamed to testServer", replaceWith = ReplaceWith("testServer"))
public fun testApplication(block: suspend ServerTestBuilder.() -> Unit): Unit = testServer(block)

/**
 * Creates a test using [TestServer].
 * To test a server Ktor application, do the following:
 * 1. Use [testServer] function to set up a configured instance of a test application running locally.
 * 2. Use the [HttpClient] instance inside a test application to make a request to your server,
 * receive a response, and make assertions.
 *
 * Suppose, you have the following route that accepts GET requests made to the `/` path
 * and responds with a plain text response:
 * ```kotlin
 * routing {
 *     get("/") {
 *         call.respondText("Hello, world!")
 *     }
 * }
 * ```
 *
 * A test for this route will look as follows:
 * ```kotlin
 * @Test
 * fun testRoot() = testApplication {
 *     val response = client.get("/")
 *     assertEquals(HttpStatusCode.OK, response.status)
 *     assertEquals("Hello, world!", response.bodyAsText())
 * }
 * ```
 *
 * _Note: If you have the `application.conf` file in the `resources` folder,
 * [testServer] loads all modules and properties specified in the configuration file automatically._
 *
 * You can learn more from [Testing](https://ktor.io/docs/testing.html).
 */
@KtorDsl
public fun testServer(block: suspend ServerTestBuilder.() -> Unit) {
    testServer(EmptyCoroutineContext, block)
}

@Deprecated(message = "Renamed to testServer", replaceWith = ReplaceWith("testServer"))
public fun testApplication(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend ServerTestBuilder.() -> Unit
): Unit = testServer(parentCoroutineContext, block)

/**
 * Creates a test using [TestServer].
 * To test a server Ktor application, do the following:
 * 1. Use [testServer] function to set up a configured instance of a test application running locally.
 * 2. Use the [HttpClient] instance inside a test application to make a request to your server,
 * receive a response, and make assertions.
 *
 * Suppose, you have the following route that accepts GET requests made to the `/` path
 * and responds with a plain text response:
 * ```kotlin
 * routing {
 *     get("/") {
 *         call.respondText("Hello, world!")
 *     }
 * }
 * ```
 *
 * A test for this route will look as follows:
 * ```kotlin
 * @Test
 * fun testRoot() = testApplication {
 *     val response = client.get("/")
 *     assertEquals(HttpStatusCode.OK, response.status)
 *     assertEquals("Hello, world!", response.bodyAsText())
 * }
 * ```
 *
 * _Note: If you have the `application.conf` file in the `resources` folder,
 * [testServer] loads all modules and properties specified in the configuration file automatically.
 *
 * You can learn more from [Testing](https://ktor.io/docs/testing.html).
 */
@KtorDsl
public fun testServer(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend ServerTestBuilder.() -> Unit
) {
    val builder = ServerTestBuilder()
        .apply {
            runBlocking(parentCoroutineContext) {
                if (parentCoroutineContext != EmptyCoroutineContext) {
                    testApplicationProperties {
                        this.parentCoroutineContext = parentCoroutineContext
                    }
                }
                block()
            }
        }

    val testApplication = builder.application
    testApplication.start()
    testApplication.stop()
}

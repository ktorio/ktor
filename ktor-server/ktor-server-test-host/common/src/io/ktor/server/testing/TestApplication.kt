/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.client.*
import io.ktor.test.dispatcher.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A client attached to [TestApplication].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ClientProvider)
 */
@KtorDsl
public interface ClientProvider {
    /**
     * Returns a client with the default configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ClientProvider.client)
     *
     * @see [testApplication]
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ClientProvider.createClient)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient
}

/**
 * A configured instance of a test application running locally.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplication)
 *
 * @see [testApplication]
 */
public class TestApplication internal constructor(
    createServer: () -> EmbeddedServer<TestApplicationEngine, TestApplicationEngine.Configuration>,
    clientProvider: ClientProvider,
    private val externalServices: ExternalServicesBuilder,
) : ClientProvider by clientProvider {

    internal enum class State {
        Created,
        Starting,
        Started,
        Stopped,
    }

    private val state = atomic(State.Created)

    internal val externalApplications by lazy { externalServices.externalApplications }
    internal val server by lazy { createServer() }
    private val applicationStarting by lazy { Job(server.engine.coroutineContext[Job]) }

    /**
     * Starts this [TestApplication] instance.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplication.start)
     */
    public suspend fun start() {
        if (state.compareAndSet(State.Created, State.Starting)) {
            try {
                server.start()
                externalServices.externalApplications.values.forEach { it.start() }
            } finally {
                state.value = State.Started
                applicationStarting.complete()
            }
        }
        if (state.value == State.Starting) {
            applicationStarting.join()
        }
    }

    /**
     * Stops this [TestApplication] instance.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplication.stop)
     */
    public fun stop() {
        state.value = State.Stopped
        server.stop()
        externalServices.externalApplications.values.forEach { it.stop() }
        client.close()
    }
}

/**
 * Creates an instance of [TestApplication] configured with the builder [block].
 * Make sure to call [TestApplication.stop] after your tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplication)
 *
 * @see [testApplication]
 */
@KtorDsl
public fun TestApplication(
    block: TestApplicationBuilder.() -> Unit
): TestApplication {
    return ApplicationTestBuilder().apply(block).application
}

/**
 * Registers mocks for external services.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ExternalServicesBuilder)
 */
@KtorDsl
public class ExternalServicesBuilder internal constructor(private val testApplicationBuilder: TestApplicationBuilder) {

    private val externalApplicationBuilders = mutableMapOf<String, () -> TestApplication>()
    internal val externalApplications: Map<String, TestApplication> by lazy {
        externalApplicationBuilders.mapValues { it.value.invoke() }
    }

    /**
     * Registers a mock for an external service specified by [hosts] and configured with [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ExternalServicesBuilder.hosts)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun hosts(vararg hosts: String, block: Application.() -> Unit) {
        check(hosts.isNotEmpty()) { "hosts can not be empty" }

        hosts.forEach {
            val protocolWithAuthority = Url(it).protocolWithAuthority
            externalApplicationBuilders[protocolWithAuthority] = {
                TestApplication {
                    environment(this@ExternalServicesBuilder.testApplicationBuilder.environmentBuilder)
                    applicationModules.add(block)
                }
            }
        }
    }
}

/**
 * A builder for [TestApplication].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder)
 */
@KtorDsl
public open class TestApplicationBuilder {

    private var built = false

    internal val externalServices = ExternalServicesBuilder(this)
    internal val applicationModules = mutableListOf<Application.() -> Unit>()
    internal var engineConfig: TestApplicationEngine.Configuration.() -> Unit = {}
    internal var environmentBuilder: ApplicationEnvironmentBuilder.() -> Unit = {}
    internal var applicationProperties: ServerConfigBuilder.() -> Unit = {}
    internal val job = Job()

    internal val properties by lazy {
        built = true
        val environment = createTestEnvironment {
            val oldConfig = config
            this@TestApplicationBuilder.environmentBuilder(this)
            if (config == oldConfig) { // the user did not set config. load the default one
                config = MapApplicationConfig()
            }
        }
        serverConfig(environment) {
            applicationModules.forEach { module(it) }
            parentCoroutineContext += job
            watchPaths = emptyList()
            developmentMode = true
            this@TestApplicationBuilder.applicationProperties(this)
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.externalServices)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun externalServices(block: ExternalServicesBuilder.() -> Unit) {
        checkNotBuilt()
        externalServices.block()
    }

    /**
     * Adds a configuration block for the [TestApplicationEngine].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.engine)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun engine(block: TestApplicationEngine.Configuration.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = engineConfig
        engineConfig = {
            oldBuilder()
            block()
        }
    }

    /**
     * Adds a configuration block for the [ServerConfig].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.serverConfig)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun serverConfig(block: ServerConfigBuilder.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = applicationProperties
        applicationProperties = {
            oldBuilder()
            block()
        }
    }

    /**
     * Builds an environment using [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.environment)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun environment(block: ApplicationEnvironmentBuilder.() -> Unit) {
        checkNotBuilt()
        val oldBuilder = environmentBuilder
        environmentBuilder = {
            oldBuilder()
            block()
        }
    }

    /**
     * Adds a module to [TestApplication].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.application)
     *
     * @see [testApplication]
     */
    @KtorDsl
    public fun application(block: Application.() -> Unit) {
        checkNotBuilt()
        applicationModules.add(block)
    }

    /**
     * Installs a [plugin] into [TestApplication]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.install)
     */
    @Suppress("UNCHECKED_CAST")
    @KtorDsl
    public fun <P : Pipeline<*, PipelineCall>, B : Any, F : Any> install(
        plugin: Plugin<P, B, F>,
        configure: B.() -> Unit = {}
    ) {
        checkNotBuilt()
        applicationModules.add { install(plugin as Plugin<ApplicationCallPipeline, B, F>, configure) }
    }

    /**
     * Installs routing into [TestApplication]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationBuilder.routing)
     */
    @KtorDsl
    public fun routing(configuration: Route.() -> Unit) {
        checkNotBuilt()
        applicationModules.add { routing(configuration) }
    }

    private fun checkNotBuilt() {
        check(!built) {
            "The test application has already been built. Make sure you configure the application " +
                "before accessing the client for the first time."
        }
    }
}

/**
 * A builder for a test that uses [TestApplication].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ApplicationTestBuilder)
 */
@KtorDsl
public class ApplicationTestBuilder : TestApplicationBuilder(), ClientProvider {

    override val client: HttpClient by lazy { createClient { } }

    internal val application: TestApplication by lazy {
        TestApplication(
            createServer = { embeddedServer },
            clientProvider = this,
            externalServices = externalServices,
        )
    }

    /**
     * Starts instance of [TestApplication].
     * Usually, users do not need to call this method because application will start on the first client call.
     * But it's still useful when you need to test your application lifecycle events.
     *
     * After calling this method, no modification of the application is allowed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.ApplicationTestBuilder.startApplication)
     */
    public suspend fun startApplication() {
        application.start()
    }

    @KtorDsl
    override fun createClient(
        block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
    ): HttpClient = HttpClient(DelegatingTestClientEngine) {
        engine {
            parentJob = this@ApplicationTestBuilder.job
            testApplicationProvder = this@ApplicationTestBuilder::application
        }
        block()
    }
}

/**
 * Creates a test using [TestApplication].
 * To test a server Ktor application, do the following:
 * 1. Use [testApplication] function to set up a configured instance of a test application running locally.
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
 * [testApplication] loads all modules and properties specified in the configuration file automatically._
 *
 * You can learn more from [Testing](https://ktor.io/docs/testing.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.testApplication)
 */
@KtorDsl
public fun testApplication(block: suspend ApplicationTestBuilder.() -> Unit): TestResult {
    return testApplication(EmptyCoroutineContext, block)
}

/**
 * Creates a test using [TestApplication].
 * To test a server Ktor application, do the following:
 * 1. Use [testApplication] function to set up a configured instance of a test application running locally.
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
 * [testApplication] loads all modules and properties specified in the configuration file automatically.
 *
 * You can learn more from [Testing](https://ktor.io/docs/testing.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.testApplication)
 */
@KtorDsl
public fun testApplication(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend ApplicationTestBuilder.() -> Unit
): TestResult = runTestWithRealTime {
    runTestApplication(parentCoroutineContext, block)
}

// allows running multiple servers during one test
// not really needed outside ktor probably
@KtorDsl
public suspend fun runTestApplication(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    val builder = ApplicationTestBuilder()
    with(builder) {
        if (parentCoroutineContext != EmptyCoroutineContext) {
            serverConfig {
                this.parentCoroutineContext = parentCoroutineContext
            }
        }
        withContext(parentCoroutineContext) { block() }
    }
    val testApplication = builder.application
    testApplication.start()
    testApplication.stop()
}

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

/**
 * Provides a client attached to [TestApplication].
 */
@KtorDsl
public interface ClientProvider {
    /**
     * Returns a client with default config
     */
    public val client: HttpClient

    /**
     * Creates a client with custom config
     */
    @KtorDsl
    public fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient
}

/**
 * A configured instance of a test application running locally.
 */
public class TestApplication internal constructor(
    private val builder: ApplicationTestBuilder
) : ClientProvider by builder {

    internal val engine by lazy { builder.engine }

    public fun stop() {
        builder.engine.stop(0, 0)
        builder.externalServices.externalApplications.values.forEach { it.stop() }
        client.close()
    }
}

/**
 * Creates an instance of [TestApplication] configured with the builder [block].
 * Make sure to call [TestApplication.stop] after your tests.
 */
@KtorDsl
public fun TestApplication(
    block: TestApplicationBuilder.() -> Unit
): TestApplication {
    val builder = ApplicationTestBuilder()
    val testApplication = TestApplication(builder)
    builder.block()
    return testApplication
}

/**
 * Registers mocks for external services.
 */
@KtorDsl
public class ExternalServicesBuilder {
    internal val externalApplications = mutableMapOf<String, TestApplication>()

    /**
     * Registers a mock for external service specified by [hosts] and configured with [block].
     */
    @KtorDsl
    public fun hosts(vararg hosts: String, block: Application.() -> Unit) {
        check(hosts.isNotEmpty()) { "hosts can not be empty" }

        val application = TestApplication { applicationModules.add(block) }
        hosts.forEach {
            val protocolWithAuthority = Url(it).protocolWithAuthority
            externalApplications[protocolWithAuthority] = application
        }
    }
}

/**
 * A builder for [TestApplication]
 */
@KtorDsl
public open class TestApplicationBuilder {

    private var built = false

    internal val externalServices = ExternalServicesBuilder()
    internal val applicationModules = mutableListOf<Application.() -> Unit>()
    internal var environmentBuilder: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
    internal val job = Job()

    internal val environment by lazy {
        built = true
        createTestEnvironment {
            config = DefaultTestConfig()
            modules.addAll(this@TestApplicationBuilder.applicationModules)
            developmentMode = true
            this@TestApplicationBuilder.environmentBuilder(this)
            parentCoroutineContext += this@TestApplicationBuilder.job
        }
    }

    internal val engine by lazy {
        TestApplicationEngine(environment)
    }

    /**
     * Builds mocks for external services using [ExternalServicesBuilder]
     */
    @KtorDsl
    public fun externalServices(block: ExternalServicesBuilder.() -> Unit) {
        checkNotBuilt()
        externalServices.block()
    }

    /**
     * Builds an environment using [block]
     */
    @KtorDsl
    public fun environment(block: ApplicationEngineEnvironmentBuilder.() -> Unit) {
        checkNotBuilt()
        environmentBuilder = block
    }

    /**
     * Adds a module to [TestApplication]
     */
    @KtorDsl
    public fun application(block: Application.() -> Unit) {
        checkNotBuilt()
        applicationModules.add(block)
    }

    /**
     * Installs a [plugin] into [TestApplication]
     */
    @Suppress("UNCHECKED_CAST")
    @KtorDsl
    public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> install(
        plugin: Plugin<P, B, F>,
        configure: B.() -> Unit = {}
    ) {
        checkNotBuilt()
        applicationModules.add { install(plugin as Plugin<ApplicationCallPipeline, B, F>, configure) }
    }

    /**
     * Installs routing into [TestApplication]
     */
    @KtorDsl
    public fun routing(configuration: Routing.() -> Unit) {
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
 * A builder for the test that uses [TestApplication]
 */
@KtorDsl
public class ApplicationTestBuilder : TestApplicationBuilder(), ClientProvider {

    override val client by lazy { createClient { } }

    @KtorDsl
    override fun createClient(
        block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
    ): HttpClient = HttpClient(DelegatingTestClientEngine) {
        engine {
            parentJob = this@ApplicationTestBuilder.job
            appEngineProvider = { this@ApplicationTestBuilder.engine }
            externalApplicationsProvider = { this@ApplicationTestBuilder.externalServices.externalApplications }
        }
        block()
    }
}

/**
 * Creates a test using [TestApplication]
 */
@KtorDsl
public fun testApplication(
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    val builder = ApplicationTestBuilder()
        .apply { runBlocking { block() } }

    val testApplication = TestApplication(builder)
    testApplication.engine.start()
    testApplication.stop()
}

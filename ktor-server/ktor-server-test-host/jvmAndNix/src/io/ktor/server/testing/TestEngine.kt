/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.testing

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*

/**
 * Creates an engine environment for a test application.
 */
public fun createTestEnvironment(
    configure: ServerEnvironmentBuilder.() -> Unit = {}
): ServerEnvironment =
    serverEnvironment {
        config = MapServerConfig("ktor.deployment.environment" to "test")
        log = KtorSimpleLogger("io.ktor.test")
        configure()
    }

/**
 * Makes a test request.
 */
public fun TestServerEngine.handleRequest(
    method: HttpMethod,
    uri: String,
    setup: TestServerRequest.() -> Unit = {}
): TestServerCall = handleRequest {
    this.uri = uri
    this.method = method
    setup()
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testServer` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withApplication(
    environment: ServerEnvironment = createTestEnvironment(),
    configure: TestServerEngine.Configuration.() -> Unit = {},
    test: TestServerEngine.() -> R
): R {
    val properties = serverParams(environment) {
        watchPaths = emptyList()
    }
    val embeddedServer = EmbeddedServer(properties, TestEngine, configure)
    embeddedServer.start()
    try {
        return embeddedServer.engine.test()
    } finally {
        embeddedServer.stop()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testServer` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(test: TestServerEngine.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testServer` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(moduleFunction: Server.() -> Unit, test: TestServerEngine.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(server)
        test()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testServer` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(
    moduleFunction: Server.() -> Unit,
    configure: TestServerEngine.Configuration.() -> Unit = {},
    test: TestServerEngine.() -> R
): R {
    return withApplication(createTestEnvironment(), configure) {
        moduleFunction(server)
        test()
    }
}

/**
 * An [ServerEngineFactory] providing a CIO-based [ServerEngine].
 */
public object TestEngine : ServerEngineFactory<TestServerEngine, TestServerEngine.Configuration> {

    override fun configuration(
        configure: TestServerEngine.Configuration.() -> Unit
    ): TestServerEngine.Configuration {
        return TestServerEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TestServerEngine.Configuration,
        serverProvider: () -> Server
    ): TestServerEngine {
        return TestServerEngine(environment, monitor, developmentMode, serverProvider, configuration)
    }
}

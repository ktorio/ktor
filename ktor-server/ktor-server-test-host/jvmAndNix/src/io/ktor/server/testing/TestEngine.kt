/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*

internal expect fun DefaultTestConfig(configPath: String? = null): ApplicationConfig

/**
 * Creates an engine environment for a test application
 */
public fun createTestEnvironment(
    configure: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): ApplicationEngineEnvironment =
    applicationEngineEnvironment {
        config = MapApplicationConfig("ktor.deployment.environment" to "test")
        log = KtorSimpleLogger("ktor.test")
        developmentMode = true
        configure()
    }

/**
 * Make a test request
 */
public fun TestApplicationEngine.handleRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall = handleRequest {
    this.uri = uri
    this.method = method
    setup()
}

/**
 * Starts a test application engine, passes it to the [test] function and stops it
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withApplication(
    environment: ApplicationEngineEnvironment = createTestEnvironment(),
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    val engine = TestApplicationEngine(environment, configure)
    engine.start()
    try {
        return engine.test()
    } finally {
        engine.stop(0L, 0L)
    }
}

/**
 * Starts a test application engine, passes it to the [test] function and stops it
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

/**
 * Starts a test application engine, passes it to the [test] function and stops it
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function and stops it
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(
    moduleFunction: Application.() -> Unit,
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    return withApplication(createTestEnvironment(), configure) {
        moduleFunction(application)
        test()
    }
}

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
public object TestEngine : ApplicationEngineFactory<TestApplicationEngine, TestApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: TestApplicationEngine.Configuration.() -> Unit
    ): TestApplicationEngine = TestApplicationEngine(environment, configure)
}

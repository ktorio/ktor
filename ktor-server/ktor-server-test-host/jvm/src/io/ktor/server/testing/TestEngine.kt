/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*

/**
 * Creates test application engine environment
 */
fun createTestEnvironment(
    configure: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): ApplicationEngineEnvironment =
    applicationEngineEnvironment {
        config = MapApplicationConfig("ktor.deployment.environment" to "test")
        log = logger().addName("ktor.test")
        configure()
    }

/**
 * Make a test request
 */
fun TestApplicationEngine.handleRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall = handleRequest {
    this.uri = uri
    this.method = method
    setup()
}

/**
 * Start test application engine, pass it to [test] function and stop it
 */
fun <R> withApplication(
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
 * Start test application engine, pass it to [test] function and stop it
 */
fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

/**
 * Start test application engine, pass it to [test] function and stop it
 */
fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

/**
 * Start test application engine, pass it to [test] function and stop it
 */
fun <R> withTestApplication(
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
object TestEngine : ApplicationEngineFactory<TestApplicationEngine, TestApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment, configure: TestApplicationEngine.Configuration.() -> Unit
    ): TestApplicationEngine = TestApplicationEngine(environment, configure)
}

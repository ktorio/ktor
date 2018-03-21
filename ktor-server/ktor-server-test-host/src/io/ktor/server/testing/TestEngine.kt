package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.engine.*
import org.slf4j.*
import java.util.concurrent.*

fun createTestEnvironment(configure: ApplicationEngineEnvironmentBuilder.() -> Unit = {}) = applicationEngineEnvironment {
    config = MapApplicationConfig("ktor.deployment.environment" to "test")
    log = LoggerFactory.getLogger("ktor.test")
    configure()
}

fun TestApplicationEngine.handleRequest(
        method: HttpMethod,
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall = handleRequest {
    this.uri = uri
    this.method = method
    setup()
}

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
        engine.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}

fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

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
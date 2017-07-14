package org.jetbrains.ktor.testing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.slf4j.*
import java.util.concurrent.*

fun createTestEnvironment(configure: ApplicationHostEnvironmentBuilder.() -> Unit = {}) = applicationHostEnvironment {
    config = MapApplicationConfig("ktor.deployment.environment" to "test")
    log = LoggerFactory.getLogger("ktor.test")
    configure()
}

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}

fun <R> withApplication(environment: ApplicationHostEnvironment = createTestEnvironment(), test: TestApplicationHost.() -> R): R {
    val host = TestApplicationHost(environment)
    host.start()
    try {
        return host.test()
    } finally {
        host.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}

fun <R> withTestApplication(test: TestApplicationHost.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationHost.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

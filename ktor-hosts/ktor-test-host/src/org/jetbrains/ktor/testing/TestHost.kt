package org.jetbrains.ktor.testing

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*

fun withApplication(environment: ApplicationHostEnvironment = emptyTestEnvironment(), test: TestApplicationHost.() -> Unit) {
    val host = TestApplicationHost(environment)
    try {
        host.test()
    } finally {
        host.dispose()
    }
}

fun emptyTestEnvironment(): ApplicationHostEnvironment {
    val environment = applicationHostEnvironment {
        config = MapApplicationConfig("ktor.deployment.environment" to "test")
        log = SLF4JApplicationLog("ktor.test")
    }
    return environment
}

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}

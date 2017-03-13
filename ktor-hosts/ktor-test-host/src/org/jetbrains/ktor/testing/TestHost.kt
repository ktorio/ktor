package org.jetbrains.ktor.testing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

@Deprecated("Use withTestApplication instead once you migrate to module functions")
inline fun <reified T : Application> withApplication(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

@Deprecated("Use withTestApplication once you migrate to module functions")
inline fun <reified T : ApplicationFeature<*, *, *>> withApplicationFeature(noinline test: TestApplicationHost.() -> Unit) {
    withApplication(T::class, test)
}

fun withApplication(environment: ApplicationEnvironment = emptyTestEnvironment(), test: TestApplicationHost.() -> Unit) {
    val host = TestApplicationHost(environment)
    try {
        host.test()
    } finally {
        host.dispose()
    }
}

fun withApplication(applicationClass: KClass<*>, test: TestApplicationHost.() -> Unit) {
    val config = MapApplicationConfig(
            "ktor.deployment.environment" to "test",
            "ktor.application.class" to applicationClass.jvmName
    )
    val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, SLF4JApplicationLog("ktor.test"), config)
    withApplication(environment, test)
}

fun emptyTestEnvironment(): ApplicationEnvironment {
    val config = MapApplicationConfig("ktor.deployment.environment" to "test")
    return BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, SLF4JApplicationLog("ktor.test"), config)
}

fun TestApplicationHost.handleRequest(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
    return handleRequest {
        this.uri = uri
        this.method = method
        setup()
    }
}

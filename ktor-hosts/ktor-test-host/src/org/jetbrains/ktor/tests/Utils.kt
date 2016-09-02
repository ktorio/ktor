package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.testing.*

object On

object It

@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

@Suppress("UNUSED_PARAMETER")
inline fun On.it(description: String, body: It.() -> Unit) = It.body()

fun withTestApplication(test: TestApplicationHost.() -> Unit) {
    withApplicationFeature<TestApplication>(test)
}

fun createTestHost(): TestApplicationHost {
    val config = MapApplicationConfig(
            "ktor.deployment.environment" to "test",
            "ktor.application.class" to TestApplication::class.qualifiedName!!
    )
    val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, SLF4JApplicationLog("ktor.test"), config)
    return TestApplicationHost(environment)
}

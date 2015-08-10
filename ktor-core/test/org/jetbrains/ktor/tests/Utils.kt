package org.jetbrains.ktor.tests

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.testing.*

object On

object It

fun on(comment: String, body: On.() -> Unit) = On.body()
inline fun On.it(description: String, body: It.() -> Unit) = It.body()

fun withTestApplication(test: TestApplicationHost.() -> Unit) {
    withApplication<TestApplication>(test)
}

fun createTestHost(): TestApplicationHost {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.deployment.environment" to "test",
                    "ktor.application.class" to TestApplication::class.qualifiedName
                 ))
    val config = ApplicationConfig(testConfig, SL4JApplicationLog("<Test>"))
    return TestApplicationHost(config)
}

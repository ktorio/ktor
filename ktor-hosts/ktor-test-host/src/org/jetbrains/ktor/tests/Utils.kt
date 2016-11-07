package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.testing.*

object On

object It

@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

@Suppress("UNUSED_PARAMETER")
inline fun On.it(description: String, body: It.() -> Unit) = It.body()

fun withTestApplication(test: TestApplicationHost.() -> Unit) {
    withApplication(emptyTestEnvironment(), test = test)
}

fun withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationHost.() -> Unit) {
    withApplication(emptyTestEnvironment()) {
        moduleFunction(application)

        test()
    }
}

fun createTestHost(): TestApplicationHost = TestApplicationHost()

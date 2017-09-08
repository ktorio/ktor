package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.features.CallLogging
import org.jetbrains.ktor.testing.createTestEnvironment
import org.jetbrains.ktor.testing.withApplication
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertTrue

class CallLoggingTest {
    @Test
    fun `can log application lifecycle events`() {
        val messages = CopyOnWriteArrayList<String>()
        val environment = createTestEnvironment {
            module {
                install(CallLogging)
            }
            
            log = object : Logger by LoggerFactory.getLogger("ktor.test") {
                override fun trace(message: String?) = add(message)

                override fun debug(message: String?) = add(message)

                override fun info(message: String?) = add(message)
                
                private fun add(message: String?) {
                    if (message != null) {
                        messages += message
                    }
                }
            }
        }
        
        withApplication(environment) {}

        assertTrue("Application starting" in messages[1])
        assertTrue("Application started" in messages[2])
        assertTrue("Application stopping" in messages[3])
        assertTrue("Application stopped" in messages[4])
    }
}

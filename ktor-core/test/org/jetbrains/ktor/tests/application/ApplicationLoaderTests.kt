package org.jetbrains.ktor.tests.application

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.junit.*
import kotlin.test.*

class ApplicationLoaderTests {

    @Test fun `invalid class name should throw`() {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.class" to "NonExistingApplicationName"
                     ))
        val config = ApplicationConfig(testConfig, ApplicationConfig::class.java.classLoader)
        assertFailsWith(ClassNotFoundException::class) { ApplicationLoader(config).application }
    }

    @Test fun `valid class name should create application`() {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.class" to "org.jetbrains.ktor.testing.TestApplication"
                     ))

        val config = ApplicationConfig(testConfig, ApplicationConfig::class.java.classLoader)
        val application = ApplicationLoader(config).application
        assertNotNull(application)
    }
}




package org.jetbrains.ktor.tests.application

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.junit.*
import kotlin.test.*

class ApplicationLoaderTests {

    Test fun `invalid class name should throw`() {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.package" to "ktor.test",
                        "ktor.application.class" to "NonExistingApplicationName"
                     ))
        val config = ApplicationConfig(testConfig)
        val result = fails { ApplicationLoader(config).application }
        assertEquals(result?.javaClass, javaClass<ClassNotFoundException>())
    }

    Test fun `valid class name should create application`() {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.package" to "ktor.tests",
                        "ktor.application.class" to "org.jetbrains.ktor.tests.TestApplication"
                     ))

        val config = ApplicationConfig(testConfig)
        val application = ApplicationLoader(config).application
        assertNotNull(application)
    }
}




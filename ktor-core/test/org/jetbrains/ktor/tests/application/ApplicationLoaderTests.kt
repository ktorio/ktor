package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import org.junit.*
import kotlin.test.*

class ApplicationLoaderTests {

    @Test fun `invalid class name should throw`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to "NonExistingApplicationName"
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        assertFailsWith(ClassNotFoundException::class) { ApplicationLoader(environment).application }
    }

    @Test fun `valid class name should create application`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to "org.jetbrains.ktor.testing.TestApplication"
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment).application
        assertNotNull(application)
    }
}




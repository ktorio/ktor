package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.reflect.jvm.*
import kotlin.test.*

class ApplicationLoaderTests {

    @Test fun `invalid class name should throw`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to "NonExistingApplicationName"
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        assertFailsWith(ClassNotFoundException::class) { ApplicationLoader(environment, false).application }
    }

    @Test fun `valid class name should create application`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to ApplicationLoaderTestApplication::class.jvmName
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
    }

    @Test fun `valid class name should create application feature`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to ApplicationLoaderTestApplicationFeature::class.jvmName
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("1", application.attributes[TestKey])
    }

    @Test fun `valid class name should create application feature with parameter`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to ApplicationLoaderTestApplicationFeatureWithEnvironment::class.jvmName
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("2", application.attributes[TestKey])
    }

    @Test fun `valid class name should lookup application feature object instance`() {
        val config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "ktor.application.class" to ApplicationLoaderTestApplicationFeatureObject::class.jvmName
        )
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("3", application.attributes[TestKey])
    }

    class ApplicationLoaderTestApplication(environment: ApplicationEnvironment) : Application(environment)

    class ApplicationLoaderTestApplicationFeature : ApplicationFeature<Application, Unit> {
        override val key = AttributeKey<Unit>("app1")
        override fun install(pipeline: Application, configure: Unit.() -> Unit) {
            pipeline.attributes.put(TestKey, "1")
        }
    }
    class ApplicationLoaderTestApplicationFeatureWithEnvironment(val environment: ApplicationEnvironment) : ApplicationFeature<Application, Unit> {
        override val key = AttributeKey<Unit>("app2")
        override fun install(pipeline: Application, configure: Unit.() -> Unit) {
            environment
            pipeline.attributes.put(TestKey, "2")
        }
    }
    object ApplicationLoaderTestApplicationFeatureObject : ApplicationFeature<Application, Unit> {
        override val key = AttributeKey<Unit>("app3")
        override fun install(pipeline: Application, configure: Unit.() -> Unit) {
            pipeline.attributes.put(TestKey, "3")
        }
    }

    companion object {
        val TestKey = AttributeKey<String>("test-key")
    }
}




package org.jetbrains.ktor.tests

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.reflect.*
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

    @Test fun `valid class name should create application module`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ApplicationLoaderTestApplicationModule::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val loader = ApplicationLoader(environment, false)
        val application = loader.application
        assertNotNull(application)
        assertEquals("1", application.attributes[TestKey])
        assertEquals(1, ApplicationLoaderTestApplicationModule.instances)
        loader.destroyApplication()
        assertEquals(0, ApplicationLoaderTestApplicationModule.instances)
    }

    @Test fun `valid class name should create application feature`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.features" to listOf(ApplicationLoaderTestApplicationFeature::class.jvmName)
                )
        ))

        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val loader = ApplicationLoader(environment, false)
        val application = loader.application
        assertNotNull(application)
        assertEquals("ApplicationLoaderTestApplicationFeature", application.attributes[TestKey])
    }

    @Test fun `valid class name should create application feature with parameter`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ApplicationLoaderTestApplicationModuleWithEnvironment::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("2", application.attributes[TestKey])
    }

    @Test fun `valid class name should lookup application feature object instance`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.features" to listOf(ApplicationLoaderTestApplicationFeatureObject::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("3", application.attributes[TestKey])
    }

    @Test fun `valid class name should lookup application module and inject application instance`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ApplicationLoaderTestApplicationModuleWithApplication::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("4", application.attributes[TestKey])
    }

    @Test fun `valid class name should lookup application module and inject both application and environment instance`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ApplicationLoaderTestApplicationModuleWithApplicationAndEnvironment::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("2+4", application.attributes[TestKey])
    }

    @Test fun `valid class name should lookup application module and respect optional parameters`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ApplicationLoaderTestApplicationModuleWithOptionalConstructorParameter::class.jvmName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("5", application.attributes[TestKey])
    }

    @Test fun `top level extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("topLevelExtensionFunction", application.attributes[TestKey])
    }

    @Test fun `top level non-extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(::topLevelFunction.fqName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("topLevelFunction", application.attributes[TestKey])
    }

    @Test fun `companion object extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Companion::class.jvmName + "." + "companionObjectExtensionFunction")
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("companionObjectExtensionFunction", application.attributes[TestKey])
    }

    @Test fun `companion object non-extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        //                "ktor.application.class" to Companion::companionObjectFunction.fqName
                        "ktor.application.modules" to listOf(Companion::class.functionFqName("companionObjectFunction"))
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("companionObjectFunction", application.attributes[TestKey])
    }

    @Test fun `companion object jvmstatic extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Companion::class.jvmName + "." + "companionObjectJvmStaticExtensionFunction")
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticExtensionFunction", application.attributes[TestKey])
    }

    @Test fun `companion object jvmstatic non-extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        //                "ktor.application.class" to Companion::companionObjectFunction.fqName
                        "ktor.application.modules" to listOf(Companion::class.functionFqName("companionObjectJvmStaticFunction"))
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticFunction", application.attributes[TestKey])
    }

    @Test fun `object holder extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ObjectModuleFunctionHolder::class.jvmName + "." + "objectExtensionFunction")
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("objectExtensionFunction", application.attributes[TestKey])
    }

    @Test fun `object holder non-extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        //                "ktor.application.class" to ObjectModuleFunctionHolder::objectFunction.fqName
                        "ktor.application.modules" to listOf(ObjectModuleFunctionHolder::class.functionFqName("objectFunction"))
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("objectFunction", application.attributes[TestKey])
    }

    @Test fun `class holder extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ClassModuleFunctionHolder::class.jvmName + "." + "classExtensionFunction")
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("classExtensionFunction", application.attributes[TestKey])
    }

    @Test fun `class holder non-extension function as module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ClassModuleFunctionHolder::classFunction.fqName)
                )))
        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("classFunction", application.attributes[TestKey])
    }

    @Test fun `no-arg module function`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        //                "ktor.application.class" to NoArgModuleFunction::main.fqName
                        "ktor.application.modules" to listOf(NoArgModuleFunction::class.functionFqName("main"))
                )))

        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals(1, NoArgModuleFunction.result)
    }

    @Test fun `multiple module functions`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleModuleFunctions::class.jvmName + ".main")
                )))

        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
    }

    @Test fun `install call logger feature`() {
        val config = HoconApplicationConfig(ConfigFactory.parseMap(
                mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.features" to listOf(CallLogging.Feature::class.jvmName)
                )))

        val environment = BasicApplicationEnvironment(ApplicationEnvironment::class.java.classLoader, NullApplicationLog(), config)
        val application = ApplicationLoader(environment, false).application
        assertNotNull(application)
        assertNotNull(application.feature(CallLogging))
    }

    class ApplicationLoaderTestApplication(environment: ApplicationEnvironment) : Application(environment)

    class ApplicationLoaderTestApplicationModule : ApplicationModule(), AutoCloseable {
        init {
            instances++
        }

        override fun close() {
            instances--
        }

        override fun Application.install() {
            attributes.put(TestKey, "1")
        }

        companion object {
            var instances = 0
        }
    }

    class ApplicationLoaderTestApplicationFeature : ApplicationFeature<Application, Any, ApplicationLoaderTestApplicationFeature> {
        override val key = AttributeKey<ApplicationLoaderTestApplicationFeature>("z")

        override fun install(pipeline: Application, configure: Any.() -> Unit): ApplicationLoaderTestApplicationFeature {
            pipeline.attributes.put(TestKey, "ApplicationLoaderTestApplicationFeature")
            return this
        }
    }

    class ApplicationLoaderTestApplicationModuleWithEnvironment(val _env: ApplicationEnvironment) : ApplicationModule() {
        override fun Application.install() {
            requireNotNull(_env)
            attributes.put(TestKey, "2")
        }
    }

    object ApplicationLoaderTestApplicationFeatureObject : ApplicationModule() {

        override fun Application.install() {
            attributes.put(TestKey, "3")
        }
    }

    class ApplicationLoaderTestApplicationModuleWithApplication(val application: Application) : ApplicationModule() {
        override fun Application.install() {
            requireNotNull(application)
            attributes.put(TestKey, "4")
        }
    }

    class ApplicationLoaderTestApplicationModuleWithApplicationAndEnvironment(val _app: Application, val _env: ApplicationEnvironment) : ApplicationModule() {
        override fun Application.install() {
            requireNotNull(_app)
            requireNotNull(_env)

            attributes.put(TestKey, "2+4")
        }
    }

    class ApplicationLoaderTestApplicationModuleWithOptionalConstructorParameter(val optionalParameter: Int = 5) : ApplicationModule() {
        override fun Application.install() {
            attributes.put(TestKey, optionalParameter.toString())
        }
    }

    object NoArgModuleFunction {
        var result = 0

        fun main() {
            result++
        }
    }

    object MultipleModuleFunctions {
        fun main() {
        }

        fun main(app: Application) {
            app.attributes.put(ApplicationLoaderTests.TestKey, "best function called")
        }
    }

    class ClassModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.classExtensionFunction() {
            attributes.put(ApplicationLoaderTests.TestKey, "classExtensionFunction")
        }

        fun classFunction(app: Application) {
            app.attributes.put(ApplicationLoaderTests.TestKey, "classFunction")
        }
    }

    object ObjectModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.objectExtensionFunction() {
            attributes.put(ApplicationLoaderTests.TestKey, "objectExtensionFunction")
        }

        fun objectFunction(app: Application) {
            app.attributes.put(ApplicationLoaderTests.TestKey, "objectFunction")
        }
    }

    companion object {
        val TestKey = AttributeKey<String>("test-key")

        private val KFunction<*>.fqName: String
            get() = javaMethod!!.declaringClass.name + "." + name

        private fun KClass<*>.functionFqName(name: String) = "$jvmName.$name"

        @Suppress("UNUSED")
        fun Application.companionObjectExtensionFunction() {
            attributes.put(ApplicationLoaderTests.TestKey, "companionObjectExtensionFunction")
        }

        fun companionObjectFunction(app: Application) {
            app.attributes.put(ApplicationLoaderTests.TestKey, "companionObjectFunction")
        }

        @Suppress("UNUSED")
        @JvmStatic
        fun Application.companionObjectJvmStaticExtensionFunction() {
            attributes.put(ApplicationLoaderTests.TestKey, "companionObjectJvmStaticExtensionFunction")
        }

        @JvmStatic
        fun companionObjectJvmStaticFunction(app: Application) {
            app.attributes.put(ApplicationLoaderTests.TestKey, "companionObjectJvmStaticFunction")
        }
    }
}

fun Application.topLevelExtensionFunction() {
    attributes.put(ApplicationLoaderTests.TestKey, "topLevelExtensionFunction")
}

fun topLevelFunction(app: Application) {
    app.attributes.put(ApplicationLoaderTests.TestKey, "topLevelFunction")
}


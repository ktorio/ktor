/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.tests.hosts

import com.typesafe.config.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.helpers.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*
import kotlin.test.*

@OptIn(EngineAPI::class)
class ApplicationEngineEnvironmentReloadingTests {

    @Test
    fun `top level extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false

            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                    )
                )
            )
        }

        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("topLevelExtensionFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `top level extension function as module function reloading stress`() {
        val environment = applicationEngineEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.deployment.watch" to listOf("ktor-server-host-common"),
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                    )
                )
            )
        }

        environment.start()
        repeat(100) {
            (environment as ApplicationEngineEnvironmentReloading).reload()
        }
        environment.stop()
    }

    @Test
    fun `top level non-extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf<KFunction0<Unit>>(::topLevelFunction).map { it.fqName }
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("topLevelFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `companion object extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.jvmName + "." + "companionObjectExtensionFunction"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("companionObjectExtensionFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `companion object non-extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.functionFqName("companionObjectFunction")
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("companionObjectFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `companion object jvmstatic extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.jvmName + "." + "companionObjectJvmStaticExtensionFunction"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticExtensionFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `companion object jvmstatic non-extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.functionFqName("companionObjectJvmStaticFunction")
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `object holder extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ObjectModuleFunctionHolder::class.jvmName + "." + "objectExtensionFunction"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("objectExtensionFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `object holder non-extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ObjectModuleFunctionHolder::class.functionFqName("objectFunction")
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("objectFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `class holder extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ClassModuleFunctionHolder::class.jvmName + "." + "classExtensionFunction"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("classExtensionFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `class holder non-extension function as module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ClassModuleFunctionHolder::classFunction.fqName)
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("classFunction", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `no-arg module function`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(NoArgModuleFunction::class.functionFqName("main"))
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals(1, NoArgModuleFunction.result)
        environment.stop()
    }

    @Test
    fun `multiple module functions`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `multiple static module functions`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleStaticModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `top level module function with default arg`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ApplicationEngineEnvironmentReloadingTests::class.jvmName + "Kt.topLevelWithDefaultArg"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("topLevelWithDefaultArg", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `static module function with default arg`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Companion::class.jvmName + ".functionWithDefaultArg")
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("functionWithDefaultArg", application.attributes[TestKey])
        environment.stop()
    }

    @Test
    fun `top level module function with jvm overloads`() {
        val environment = applicationEngineEnvironment {
            developmentMode = false
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ApplicationEngineEnvironmentReloadingTests::class.jvmName + "Kt.topLevelWithJvmOverloads"
                        )
                    )
                )
            )
        }
        environment.start()
        val application = environment.application
        assertNotNull(application)
        assertEquals("topLevelWithJvmOverloads", application.attributes[TestKey])
        environment.stop()
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
            app.attributes.put(TestKey, "best function called")
        }
    }

    object MultipleStaticModuleFunctions {
        @JvmStatic
        fun main() {
        }

        @JvmStatic
        fun main(app: Application) {
            app.attributes.put(TestKey, "best function called")
        }
    }

    class ClassModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.classExtensionFunction() {
            attributes.put(TestKey, "classExtensionFunction")
        }

        fun classFunction(app: Application) {
            app.attributes.put(TestKey, "classFunction")
        }
    }

    object ObjectModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.objectExtensionFunction() {
            attributes.put(TestKey, "objectExtensionFunction")
        }

        fun objectFunction(app: Application) {
            app.attributes.put(TestKey, "objectFunction")
        }
    }

    companion object {
        val TestKey = AttributeKey<String>("test-key")

        private val KFunction<*>.fqName: String
            get() = javaMethod!!.declaringClass.name + "." + name

        private fun KClass<*>.functionFqName(name: String) = "$jvmName.$name"

        @Suppress("UNUSED")
        fun Application.companionObjectExtensionFunction() {
            attributes.put(TestKey, "companionObjectExtensionFunction")
        }

        fun companionObjectFunction(app: Application) {
            app.attributes.put(TestKey, "companionObjectFunction")
        }

        @Suppress("UNUSED")
        @JvmStatic
        fun Application.companionObjectJvmStaticExtensionFunction() {
            attributes.put(TestKey, "companionObjectJvmStaticExtensionFunction")
        }

        @JvmStatic
        fun companionObjectJvmStaticFunction(app: Application) {
            app.attributes.put(TestKey, "companionObjectJvmStaticFunction")
        }

        @JvmStatic
        fun Application.functionWithDefaultArg(test: Boolean = false) {
            attributes.put(TestKey, "functionWithDefaultArg")
        }
    }

    @Test
    fun `application is available before environment start`() {
        val env = dummyEnv()
        val app = env.application
        env.start()
        assertEquals(app, env.application)
    }

    @Test
    fun `completion handler is invoked when attached before environment start`() {
        val env = dummyEnv()

        var invoked = false
        env.application.coroutineContext[Job]?.invokeOnCompletion {
            invoked = true
        }

        env.start()
        env.stop()

        assertTrue(invoked, "On completion handler wasn't invoked")
    }

    @Test
    fun `interceptor is invoked when added before environment start`() {
        val engine = TestApplicationEngine(createTestEnvironment())
        engine.application.intercept(ApplicationCallPipeline.Features) {
            call.response.header("Custom", "Value")
        }
        engine.start()

        try {
            engine.apply {
                application.routing {
                    get("/") {
                        call.respondText { "Hello" }
                    }
                }

                assertEquals("Value", handleRequest(HttpMethod.Get, "/").response.headers["Custom"])
            }
        } catch (cause: Throwable) {
            fail("Failed with an exception: ${cause.message}")
        } finally {
            engine.stop(0L, 0L)
        }
    }

    private fun dummyEnv() = ApplicationEngineEnvironmentReloading(
        classLoader = this::class.java.classLoader,
        log = NOPLogger.NOP_LOGGER,
        config = MapApplicationConfig(),
        connectors = emptyList(),
        modules = emptyList()
    )
}

fun Application.topLevelExtensionFunction() {
    attributes.put(ApplicationEngineEnvironmentReloadingTests.TestKey, "topLevelExtensionFunction")
}

fun topLevelFunction(app: Application) {
    app.attributes.put(ApplicationEngineEnvironmentReloadingTests.TestKey, "topLevelFunction")
}

@Suppress("unused")
fun topLevelFunction() {
    error("Shouldn't be invoked")
}

fun Application.topLevelWithDefaultArg(testing: Boolean = false) {
    attributes.put(ApplicationEngineEnvironmentReloadingTests.TestKey, "topLevelWithDefaultArg")
}

@JvmOverloads
fun Application.topLevelWithJvmOverloads(testing: Boolean = false) {
    attributes.put(ApplicationEngineEnvironmentReloadingTests.TestKey, "topLevelWithJvmOverloads")
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.tests.hosts

import com.typesafe.config.ConfigFactory
import io.ktor.client.request.*
import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.tests.hosts.EmbeddedServerReloadingTests.Companion.addLoadedModule
import io.ktor.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.net.URLClassLoader
import kotlin.io.path.createTempDirectory
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction0
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EmbeddedServerReloadingTests {

    @Test
    fun `top level extension functions as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Application::topLevelExtensionFunction.fqName,
                            Application::topLevelSuspendExtensionFunction.fqName,
                        )
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelExtensionFunction", application.attributes[TestKey])
        assertEquals("topLevelSuspendExtensionFunction", application.attributes[TestKey2])
        server.stop()
    }

    @Test
    fun `top level extension function as module function reloading stress`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.deployment.watch" to listOf("ktor-server-core"),
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                    )
                )
            )
        }

        val props = serverConfig(environment)
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        repeat(100) {
            server.reload()
        }
        server.stop()
    }

    @Test
    fun `top level non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf<KFunction0<Unit>>(::topLevelFunction).map { it.fqName }
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object non-extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object jvmstatic extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object jvmstatic non-extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `object holder extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("objectExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `object holder non-extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("objectFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `class holder extension function as module function`() {
        val environment = applicationEnvironment {
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
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("classExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `class holder non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ClassModuleFunctionHolder::classFunction.fqName)
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("classFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `no-arg module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(NoArgModuleFunction::class.functionFqName("main"))
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals(1, NoArgModuleFunction.result)
        server.stop()
    }

    @Test
    fun `multiple module functions`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `multiple static module functions`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleStaticModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `top level module function with default arg`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Application::defaultArgBoolean.fqName,
                            Application::defaultArgContainingApplicationWord.fqName,
                            Application::defaultArgInline.fqName,
                        )
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals(
            setOf(
                "defaultArgBoolean",
                "defaultArgContainingApplicationWord",
                "defaultArgInline",
            ),
            application.loadedModules,
        )
        server.stop()
    }

    @Test
    fun `static module function with default arg`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Companion::class.jvmName + ".functionWithDefaultArg")
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("functionWithDefaultArg", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `top level module function with jvm overloads`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            EmbeddedServerReloadingTests::class.jvmName + "Kt.topLevelWithJvmOverloads"
                        )
                    )
                )
            )
        }
        val props = serverConfig(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelWithJvmOverloads", application.attributes[TestKey])
        server.stop()
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
        fun Application.classExtensionFunction() {
            attributes.put(TestKey, "classExtensionFunction")
        }

        fun classFunction(app: Application) {
            app.attributes.put(TestKey, "classFunction")
        }
    }

    object ObjectModuleFunctionHolder {
        fun Application.objectExtensionFunction() {
            attributes.put(TestKey, "objectExtensionFunction")
        }

        fun objectFunction(app: Application) {
            app.attributes.put(TestKey, "objectFunction")
        }
    }

    companion object {
        val TestKey = AttributeKey<String>("test-key")
        val TestKey2 = AttributeKey<String>("test-key2")
        private val LoadedModulesKey = AttributeKey<MutableSet<String>>("loaded-modules")

        val Application.loadedModules: Set<String>
            get() = attributes.getOrNull(LoadedModulesKey).orEmpty()

        fun Application.addLoadedModule(name: String) {
            attributes.computeIfAbsent(LoadedModulesKey) { mutableSetOf() }.add(name)
        }

        private val KFunction<*>.fqName: String
            get() = javaMethod!!.declaringClass.name + "." + name

        private fun KClass<*>.functionFqName(name: String) = "$jvmName.$name"

        fun Application.companionObjectExtensionFunction() {
            attributes.put(TestKey, "companionObjectExtensionFunction")
        }

        fun companionObjectFunction(app: Application) {
            app.attributes.put(TestKey, "companionObjectFunction")
        }

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
    fun `auto-reload is disabled when classloader is not a URLClassLoader`() {
        // Simulate a fat-JAR launcher classloader (e.g. Spring Boot LaunchedClassLoader),
        // which is NOT a URLClassLoader. Using OverridingClassLoader in this case causes
        // LinkageError / ClassCastException because it can't handle the custom URL scheme.
        val realLoader = Thread.currentThread().contextClassLoader
        val fatJarSimulator = object : ClassLoader(realLoader) {
            override fun toString() = "FatJarSimulatorClassLoader"
        }

        var capturedClassLoader: ClassLoader? = null
        val environment = applicationEnvironment {
            classLoader = fatJarSimulator
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(mapOf("ktor.deployment.watch" to listOf("ktor-server-core")))
            )
        }
        val props = serverConfig(environment) {
            developmentMode = true
            module { capturedClassLoader = Thread.currentThread().contextClassLoader }
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        assertNotNull(server.application)

        // When the base classloader is not a URLClassLoader, auto-reload must be disabled:
        // the module must run under the original base classloader, not under OverridingClassLoader.
        assertSame(
            fatJarSimulator,
            capturedClassLoader,
            "Expected the original classloader. " +
                "Auto-reload should be disabled for non-URLClassLoader environments (fat-JARs)."
        )

        server.stop()
    }

    class AppEvent(val app: Application, val type: Type) {
        enum class Type {
            Starting,
            Stopping,
            Stopped
        }
    }

    @Test
    fun `failed explicit reload stops application and allows retry`() {
        var moduleCallCount = 0
        var appShouldFailToStart = false
        val lifecycle = mutableListOf<AppEvent>()

        val environment = applicationEnvironment {
            classLoader = this::class.java.classLoader
            log = NOPLogger.NOP_LOGGER
            config = MapApplicationConfig()
        }

        val props = serverConfig(environment) {
            developmentMode = false
            module {
                val callIndex = moduleCallCount++
                attributes.put(TestKey, "load-$callIndex")
                if (appShouldFailToStart) {
                    error("Simulated reload failure on call #$callIndex")
                }
            }
        }
        val server = EmbeddedServer(props, DummyEngineFactory).apply {
            monitor.subscribe(ApplicationStarting) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Starting) }
            monitor.subscribe(ApplicationStopping) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Stopping) }
            monitor.subscribe(ApplicationStopped) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Stopped) }
            start()
        }

        val initialApp = server.application
        assertEquals("load-0", initialApp.attributes[TestKey])
        assertTrue(initialApp.isActive)

        appShouldFailToStart = true
        assertFailsWith<IllegalStateException> { server.reload() }
        assertEquals(2, moduleCallCount)
        assertFalse(initialApp.isActive)

        assertFailsWith<IllegalStateException> { server.application }

        val stoppedIndex = lifecycle.indexOfFirst { it.type == AppEvent.Type.Stopped && it.app === initialApp }
        val nextStartingIndex = lifecycle.indexOfFirst { it.type == AppEvent.Type.Starting && it.app !== initialApp }
        assertTrue(
            stoppedIndex in 0..<nextStartingIndex,
            "Expected old generation to stop before the replacement starts: $lifecycle"
        )

        val failedApp = lifecycle.first { it.type == AppEvent.Type.Starting && it.app !== initialApp }.app
        assertEquals(
            1,
            lifecycle.count { it.type == AppEvent.Type.Stopping && it.app === failedApp },
            "Failed generation should be cleaned exactly once: $lifecycle"
        )
        assertTrue(
            lifecycle.any { it.type == AppEvent.Type.Stopped && it.app === failedApp },
            "Expected failed partial generation to be cleaned up: $lifecycle"
        )

        appShouldFailToStart = false
        server.reload()
        assertEquals(3, moduleCallCount)
        assertEquals("load-2", server.application.attributes[TestKey])
        assertTrue(server.application.isActive)

        server.stop()
    }

    @Test
    fun `auto-reload retries only after another watched file change`() {
        val watchDir = createTempDirectory(prefix = "ktor-autoreload-watch-").toFile().also { it.deleteOnExit() }
        val marker = File(watchDir, "marker.txt").also { it.writeText("initial") }
        val watchToken = watchDir.name

        val parentLoader = EmbeddedServerReloadingTests::class.java.classLoader
        val reloadClassLoader = URLClassLoader(arrayOf(watchDir.toURI().toURL()), parentLoader)

        assertTrue(
            checkUrlMatches(watchDir.toURI().toURL(), watchToken),
            "Watch token must match the temp directory URL"
        )
        assertTrue(reloadClassLoader.urLs.toList().any { checkUrlMatches(it, watchToken) })

        var generation = 0
        var appShouldFailToStart = false
        val lifecycle = mutableListOf<AppEvent>()

        val environment = applicationEnvironment {
            classLoader = reloadClassLoader
            log = NOPLogger.NOP_LOGGER
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf("ktor.deployment.watch" to listOf(watchToken))
                )
            )
        }

        val props = serverConfig(environment) {
            developmentMode = true
            watchPaths = listOf(watchToken)
            module {
                val current = ++generation
                attributes.put(TestKey, "gen-$current")
                if (appShouldFailToStart) {
                    error("Simulated auto-reload failure for generation $current")
                }
            }
        }
        val server = EmbeddedServer(props, DummyEngineFactory).apply {
            monitor.subscribe(ApplicationStarting) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Starting) }
            monitor.subscribe(ApplicationStopping) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Stopping) }
            monitor.subscribe(ApplicationStopped) { lifecycle += AppEvent(app = it, type = AppEvent.Type.Stopped) }
        }

        try {
            server.start()
            val firstApp = server.application
            assertEquals("gen-1", firstApp.attributes[TestKey])
            assertEquals(1, generation)

            appShouldFailToStart = true
            marker.writeText("change-1-${System.nanoTime()}")
            awaitUntil {
                server.probeApplication()
                generation > 1
            }
            assertEquals(2, generation)
            assertFalse(firstApp.isActive)

            val stoppedIndex = lifecycle.indexOfFirst { it.type == AppEvent.Type.Stopped && it.app === firstApp }
            val failedStartingIndex = lifecycle.indexOfFirst {
                it.type == AppEvent.Type.Starting && it.app !== firstApp
            }
            assertTrue(
                stoppedIndex in 0..<failedStartingIndex,
                "Expected no application overlap on failed auto-reload: $lifecycle"
            )

            val failedApp = lifecycle[failedStartingIndex].app
            assertEquals(
                1,
                lifecycle.count { it.type == AppEvent.Type.Stopping && it.app === failedApp },
                "Failed partial generation should be cleaned exactly once: $lifecycle"
            )

            assertFailsWith<IllegalStateException> { server.application }
            assertEquals(2, generation, "Must not retry creation without another file change")

            appShouldFailToStart = false
            marker.writeText("change-2-${System.nanoTime()}")
            awaitUntil {
                server.probeApplication()
                generation > 2
            }

            assertEquals("gen-3", server.application.attributes[TestKey])
            assertEquals(3, generation)
            assertTrue(server.application.isActive)
        } finally {
            server.stop()
            reloadClassLoader.close()
            watchDir.deleteRecursively()
        }
    }

    // Triggers change detection via application lookup; ignores failures while none is loaded.
    private fun EmbeddedServer<*, *>.probeApplication() {
        runCatching { application }
    }

    private fun awaitUntil(timeout: Duration = 10.seconds, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for condition" }
            Thread.sleep(100)
        }
    }

    @Test
    fun `application is available before environment start`() {
        val env = dummyEnv()
        val props = serverConfig(env)
        val server = EmbeddedServer(props, DummyEngineFactory)
        val app = server.application
        server.start()
        assertEquals(app, server.application)
    }

    @Test
    fun `completion handler is invoked when attached before environment start`() {
        val env = dummyEnv()
        val props = serverConfig(env)
        val server = EmbeddedServer(props, DummyEngineFactory)
        val job = server.application.coroutineContext[Job]!!

        var invoked = false
        job.invokeOnCompletion {
            invoked = true
        }

        server.start()
        server.stop()

        runBlocking { job.join() }

        assertTrue(invoked, "On completion handler wasn't invoked")
    }

    @Test
    fun `interceptor is invoked when added before environment start`() {
        val server = EmbeddedServer(serverConfig(), TestEngine) {}
        val engine = server.engine
        server.application.intercept(ApplicationCallPipeline.Plugins) {
            call.response.header("Custom", "Value")
        }
        server.start()

        try {
            server.application.routing {
                get("/") {
                    call.respondText { "Hello" }
                }
            }

            val response = runBlocking { engine.client.get("/") }
            assertEquals("Value", response.headers["Custom"])
        } catch (cause: Throwable) {
            fail("Failed with an exception: ${cause.message}")
        } finally {
            server.stop(0L, 0L)
        }
    }

    private fun dummyEnv() = applicationEnvironment {
        classLoader = this::class.java.classLoader
        log = NOPLogger.NOP_LOGGER
        config = MapApplicationConfig()
    }

    private object DummyEngineFactory :
        ApplicationEngineFactory<DummyEngine, BaseApplicationEngine.Configuration> {

        override fun configuration(
            configure: BaseApplicationEngine.Configuration.() -> Unit
        ): BaseApplicationEngine.Configuration {
            return BaseApplicationEngine.Configuration().apply(configure)
        }

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: BaseApplicationEngine.Configuration,
            applicationProvider: () -> Application
        ): DummyEngine {
            return DummyEngine
        }
    }

    private object DummyEngine : BaseApplicationEngine(applicationEnvironment { }, Events(), false) {
        override fun start(wait: Boolean): ApplicationEngine = this

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {}
    }
}

// some weirdness with the compiler ignores default in expect
fun EmbeddedServer<*, *>.start() = start(wait = false)

fun Application.topLevelExtensionFunction() {
    attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelExtensionFunction")
}

suspend fun Application.topLevelSuspendExtensionFunction() {
    delay(0.milliseconds)
    attributes.put(EmbeddedServerReloadingTests.TestKey2, "topLevelSuspendExtensionFunction")
}

fun topLevelFunction(app: Application) {
    app.attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelFunction")
}

@Suppress("unused")
fun topLevelFunction() {
    error("Shouldn't be invoked")
}

fun Application.defaultArgBoolean(testing: Boolean = false) {
    addLoadedModule("defaultArgBoolean")
}

fun Application.defaultArgContainingApplicationWord(configure: Application.() -> Unit = {}) {
    addLoadedModule("defaultArgContainingApplicationWord")
}

@JvmInline
value class InstanceId(val value: String)

fun Application.defaultArgInline(id: InstanceId = InstanceId("default")) {
    addLoadedModule("defaultArgInline")
}

@JvmOverloads
fun Application.topLevelWithJvmOverloads(testing: Boolean = false) {
    attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelWithJvmOverloads")
}

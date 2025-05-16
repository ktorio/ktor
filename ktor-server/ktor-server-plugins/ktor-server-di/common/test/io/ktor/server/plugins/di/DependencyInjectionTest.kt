/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.runTestWithRealTime
import io.ktor.util.logging.Logger
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.*

internal const val HELLO = "Hello, world!"
internal const val HELLO_CUSTOMER = "Hello, customer!"

internal interface GreetingService {
    fun hello(): String
}

internal class GreetingServiceImpl : GreetingService {
    override fun hello() = HELLO
}

internal class BankGreetingService : GreetingService {
    override fun hello() = HELLO_CUSTOMER
}

internal interface BankService {
    fun deposit(money: Int)
    fun withdraw(money: Int)
    fun balance(): Int
}

internal class BankServiceImpl : BankService {
    private var balance = 0
    override fun balance(): Int = balance
    override fun deposit(money: Int) {
        balance += money
    }
    override fun withdraw(money: Int) {
        balance -= money
    }
}

internal data class BankTeller(
    val greetingService: GreetingService = BankGreetingService(),
    val bankService: BankService
) : GreetingService by greetingService, BankService by bankService

data class WorkExperience(val jobs: List<PaidWork>)

data class PaidWork(val requiredExperience: WorkExperience)

interface Closer {
    fun closeMe()
}

@Serializable
data class ConnectionConfig(
    val url: String,
    val username: String,
    val password: String,
)

interface DataSource {
    suspend fun connect()
}

class FakeLogger {
    val lines = mutableListOf<String>()

    fun info(message: String) {
        lines += message
    }
}

data class DataSourceImpl(
    val config: ConnectionConfig,
    val logger: FakeLogger
) : DataSource {
    override suspend fun connect() {
        logger.info("Connecting to ${config.url}...")
    }
}

class DependencyInjectionTest {

    @Test
    fun missing() = runTestWithRealTime {
        assertFailsWith<MissingDependencyException> {
            testDI {
                val service: GreetingService by dependencies
                fail("Should fail but found $service")
            }
        }
    }

    @Test
    fun `resolution out of order`() = runTestDI {
        assertFailsWith<OutOfOrderDependencyException> {
            dependencies { provide<GreetingService> { GreetingServiceImpl() } }
            assertNotNull(dependencies.resolve<GreetingService>())
            dependencies { provide<String> { "Hello" } }
        }
    }

    @Test
    fun `conflicting declarations`() = runTestDI {
        assertFailsWith<DuplicateDependencyException> {
            dependencies { provide<GreetingService> { GreetingServiceImpl() } }
            dependencies { provide<GreetingService> { BankGreetingService() } }
        }
    }

    @Test
    fun `fails on server startup`() = runTest {
        val infoLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        assertFailsWith<DependencyInjectionException> {
            runTestApplication {
                environment {
                    log = object : Logger by log {
                        override fun info(message: String) {
                            infoLogs += message
                        }
                        override fun error(message: String) {
                            errorLogs += message
                        }
                    }
                }
                application {
                    dependencies {
                        provide<BankService> { BankServiceImpl() }
                    }
                    val service: GreetingService by dependencies
                    routing {
                        get("/ok") {
                            call.respondText("OK")
                        }
                        get("/hello") {
                            call.respondText(service.hello())
                        }
                    }
                }

                val response = client.get("/ok").bodyAsText()
                fail("Expected to throw on missing dependency but got $response")
            }
        }
        assertFalse(
            infoLogs.any { message ->
                "Application started" in message
            }
        )
        assertEquals(2, errorLogs.size)
        val (missing, summary) = errorLogs
        val missingKey = DependencyKey<GreetingService>()
        assertContains(missing, "Cannot resolve $missingKey")
        assertContains(summary, "Dependency resolution failed")
        assertContains(summary, "$missingKey: Missing declaration")
    }

    @Test
    fun `last entry wins for tests`() = testApplication {
        application {
            dependencies { provide<GreetingService> { GreetingServiceImpl() } }
            dependencies { provide<GreetingService> { BankGreetingService() } }

            val service: GreetingService by dependencies
            assertEquals(HELLO_CUSTOMER, service.hello())
        }
    }

    @Test
    fun `circular dependencies`() = runTestWithRealTime {
        assertFailsWith<CircularDependencyException> {
            testDI {
                dependencies {
                    provide<WorkExperience> { WorkExperience(resolve()) }
                    provide<PaidWork> { PaidWork(resolve()) }
                    provide<List<PaidWork>> { listOf(resolve()) }
                }
                val eligibleJobs: List<PaidWork> by dependencies
                fail("This should fail but returned $eligibleJobs")
            }
        }
    }

    @Test
    fun basic() = runTestDI {
        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
        }

        val service: GreetingService by dependencies
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun caching() = runTestDI {
        var callCount = 0
        dependencies {
            provide<GreetingService> {
                callCount++
                GreetingServiceImpl()
            }
        }

        val delegatedService: GreetingService by dependencies
        assertEquals(0, callCount, "Delegated properties should be lazily resolved")
        assertEquals(HELLO, delegatedService.hello())
        assertEquals(1, callCount)
        assertEquals(HELLO, dependencies.resolve<GreetingService>().hello())
        assertEquals(HELLO, dependencies.resolve<GreetingService>().hello())
        assertEquals(1, callCount)
    }

    @Test
    fun `basic with qualifier`() = runTestDI {
        dependencies {
            provide<GreetingService>(name = "test") { GreetingServiceImpl() }
        }

        val service: GreetingService by dependencies.named("test")
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun lambdas() = runTestDI {
        dependencies {
            provide<() -> GreetingService> { { GreetingServiceImpl() } }
        }

        val service: () -> GreetingService by dependencies
        assertEquals(HELLO, service().hello())
    }

    @Test
    fun parameterized() = runTestDI {
        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
            provide<List<GreetingService>> { listOf(resolve(), resolve()) }
        }

        val services: List<GreetingService> by dependencies
        for (service in services) {
            assertEquals(HELLO, service.hello())
            assertFailsWith<MissingDependencyException> {
                dependencies.resolve<List<BankService>>()
            }
        }
    }

    @Test
    fun arguments() = runTestDI {
        val expectedStringList = listOf("one", "two")

        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
            provide<List<String>>("my-strings") {
                expectedStringList
            }
            provide<List<Any>>("my-list") {
                listOf(
                    resolve<GreetingService>(),
                    resolve<List<String>>("my-strings"),
                )
            }
        }

        val service: GreetingService by dependencies
        val stringList: List<String> by dependencies.named("my-strings")
        val anyList: List<Any> by dependencies.named("my-list")

        assertEquals(HELLO, service.hello())
        assertEquals(expectedStringList, stringList)
        val (first, second) = anyList
        assertIs<GreetingService>(first)
        assertEquals(HELLO, first.hello())
        assertEquals(expectedStringList, second)
    }

    @Test
    fun `custom provider`() = testApplication {
        val assignmentKeys = mutableListOf<DependencyKey>()
        install(DI) {
            val delegate = MapDependencyProvider()
            provider = object : DependencyProvider by delegate {
                override fun <T> set(
                    key: DependencyKey,
                    value: DependencyResolver.() -> T
                ) {
                    assignmentKeys += key
                    delegate.set(key, value)
                }
            }
        }
        application {
            dependencies {
                provide<GreetingService> { GreetingServiceImpl() }
            }
            assertEquals(listOf(DependencyKey<GreetingService>()), assignmentKeys)
        }
    }

    @Test
    fun `async support`() = testApplication(Dispatchers.Unconfined) {
        val greeter = GreetingServiceImpl()
        val bank = BankServiceImpl()
        val resolutionChannel = Channel<String>(3)

        application {
            dependencies {
                provideAsync<GreetingService> {
                    delay(100)
                    greeter.also {
                        resolutionChannel.trySend("greeting").getOrThrow()
                    }
                }
                provideAsync<BankService> {
                    delay(50)
                    bank.also {
                        resolutionChannel.trySend("bank").getOrThrow()
                    }
                }
                provideAsync<BankTeller> {
                    BankTeller(resolveAwait(), resolveAwait()).also {
                        resolutionChannel.trySend("teller").getOrThrow()
                    }
                }
            }
            val bankService: Deferred<BankService> by dependencies
            routing {
                get("/hello") {
                    val service: GreetingService = dependencies.resolveAwait()
                    call.respondText(service.hello())
                }
                get("/balance") {
                    val service: BankService = bankService.await()
                    call.respondText(service.balance().toString())
                }
                get("/bank-teller") {
                    val service: BankTeller = dependencies.resolveAwait()
                    call.respondText("${service.hello()}, your balance is ${service.balance()}")
                }
            }
        }
        val responses = Array<HttpResponse?>(3) { null }
        coroutineScope {
            listOf("/hello", "/balance", "/bank-teller").mapIndexed { index, url ->
                launch {
                    responses[index] = client.get(url)
                }
            }.joinAll()
        }
        val (helloResponse, balanceResponse, bankTellerResponse) = responses.map { it!!.bodyAsText() }

        assertEquals(greeter.hello(), helloResponse)
        assertEquals(bank.balance().toString(), balanceResponse)
        assertEquals("${greeter.hello()}, your balance is ${bank.balance()}", bankTellerResponse)

        resolutionChannel.close()
        val resolutions = resolutionChannel.consumeAsFlow().toList()
        assertEquals(listOf("bank", "greeting", "teller"), resolutions)
    }

    @Test
    fun `async shutdown`() = runTest {
        val registryDeferred = CompletableDeferred<DependencyRegistry>()
        val application = launch {
            runTestApplication(coroutineContext) {
                application {
                    dependencies {
                        provideAsync<GreetingService> {
                            awaitCancellation()
                        }
                    }
                    routing {
                        get("/hello") {
                            call.launch {
                                delay(50)
                                registryDeferred.complete(dependencies)
                            }
                            val service: GreetingService = dependencies.resolveAwait()
                            call.respondText(service.hello())
                        }
                    }
                }
                val greeting = client.get("/hello").bodyAsText()
                fail("Should not reach here, but got $greeting")
            }
        }
        val registry = registryDeferred.await()
        application.cancelAndJoin()
        assertFalse(registry.isActive)
        assertFailsWith<CancellationException> {
            registry.resolveAwait<GreetingService>()
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `custom reflection`() = runTestDI({
        reflection = object : DependencyReflection {
            override fun <T : Any> create(
                kClass: KClass<T>,
                init: (DependencyKey) -> Any
            ): T = when (kClass) {
                GreetingService::class -> GreetingServiceImpl() as T
                else -> fail("Unexpected class $kClass")
            }
        }
    }) {
        val service: GreetingService = dependencies.create()
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun `external maps`() = runTestDI({
        include(dependencyMapOf(DependencyKey<GreetingService>() to GreetingServiceImpl()))
    }) {
        val service: GreetingService by dependencies
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun `external map order precedence`() = runTestDI({
        include(dependencyMapOf(DependencyKey<GreetingService>() to GreetingServiceImpl()))
        include(dependencyMapOf(DependencyKey<GreetingService>() to BankGreetingService()))
    }) {
        val service: GreetingService by dependencies
        assertEquals(HELLO_CUSTOMER, service.hello())
    }

    @Test
    fun `external map declarations precedence`() = runTestDI({
        include(dependencyMapOf(DependencyKey<GreetingService>() to GreetingServiceImpl()))
    }) {
        dependencies.provide<GreetingService> { BankGreetingService() }

        val service: GreetingService by dependencies
        assertEquals(HELLO_CUSTOMER, service.hello())
    }

    @Test
    fun `unnamed key mapping`() = runTestDI({
        provider {
            keyMapping = Unnamed
        }
    }) {
        dependencies.provide<GreetingService>("bank") { BankGreetingService() }

        val named: GreetingService by dependencies.named("bank")
        val unnamed: GreetingService by dependencies
        assertEquals(HELLO_CUSTOMER, named.hello())
        assertEquals(HELLO_CUSTOMER, unnamed.hello())
    }

    @Test
    fun cleanup() = runTest {
        val closed = mutableSetOf<Any>()
        val closer1 = object : Closer {
            override fun closeMe() {
                closed += this
            }
        }
        val closer2 = object : Closer {
            override fun closeMe() {
                closed += this
            }
        }
        val autoCloseable = object : AutoCloseable {
            override fun close() {
                closed += this
            }
        }

        runTestApplication {
            application {
                dependencies {
                    provide<AutoCloseable> { autoCloseable }
                    provide<Closer> { closer1 } cleanup { it.closeMe() }

                    key<Closer>("second") {
                        provide { closer2 }
                        cleanup { it.closeMe() }
                    }
                }
                val closer: Closer by dependencies
                assertEquals(closer1, closer)
            }
        }

        assertEquals(
            listOf(closer2, closer1, autoCloseable),
            closed.toList(),
            "Expected all dependencies to be closed in the correct order"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun dependencyMapOf(vararg entries: Pair<DependencyKey, Any>): DependencyMap {
        val map = mapOf(*entries)
        return object : DependencyMap {
            override fun contains(key: DependencyKey): Boolean =
                map.containsKey(key)
            override fun <T> get(key: DependencyKey): T =
                map[key] as T
        }
    }

    private fun runTestDI(
        pluginInstall: DependencyInjectionConfig.() -> Unit = {},
        block: Application.() -> Unit
    ): TestResult = runTestWithRealTime {
        testDI(pluginInstall, block)
    }

    // Use default DI configuration (not test mode)
    private suspend fun testDI(
        pluginInstall: DependencyInjectionConfig.() -> Unit = {},
        block: Application.() -> Unit
    ) = runTestApplication {
        install(DI) {
            pluginInstall()
            if (!providerChanged) {
                provider = MapDependencyProvider()
            }
        }
        application {
            block()
        }
    }
}

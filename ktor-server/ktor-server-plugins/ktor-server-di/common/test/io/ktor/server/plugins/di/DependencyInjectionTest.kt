/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
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

class DependencyInjectionTest {

    @Test
    fun missing() {
        assertFailsWith<MissingDependencyException> {
            testDI {
                val service: GreetingService by dependencies
                fail("Should fail but found $service")
            }
        }
    }

    @Test
    fun `resolution out of order`() = testDI {
        assertFailsWith<OutOfOrderDependencyException> {
            dependencies { provide<GreetingService> { GreetingServiceImpl() } }
            assertNotNull(dependencies.resolve<GreetingService>())
            dependencies { provide<String> { "Hello" } }
        }
    }

    @Test
    fun `conflicting declarations`() = testDI {
        assertFailsWith<DuplicateDependencyException> {
            dependencies { provide<GreetingService> { GreetingServiceImpl() } }
            dependencies { provide<GreetingService> { BankGreetingService() } }
        }
    }

    @Test
    fun `fails on server startup`() {
        assertFailsWith<MissingDependencyException> {
            testApplication {
                application {
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
    fun `circular dependencies`() {
        assertFailsWith<CircularDependencyException> {
            testDI {
                dependencies {
                    provide<WorkExperience> { WorkExperience(this.resolve()) }
                    provide<PaidWork> { PaidWork(this.resolve()) }
                    provide<List<PaidWork>> { listOf(this.resolve()) }
                }
                val eligibleJobs: List<PaidWork> by dependencies
                fail("This should fail but returned $eligibleJobs")
            }
        }
    }

    @Test
    fun basic() = testDI {
        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
        }

        val service: GreetingService by dependencies
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun caching() = testDI {
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
    fun `basic with qualifier`() = testDI {
        dependencies {
            provide<GreetingService>(name = "test") { GreetingServiceImpl() }
        }

        val service: GreetingService by dependencies.named("test")
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun lambdas() = testDI {
        dependencies {
            provide<() -> GreetingService> { { GreetingServiceImpl() } }
        }

        val service: () -> GreetingService by dependencies
        assertEquals(HELLO, service().hello())
    }

    @Test
    fun parameterized() = testDI {
        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
            provide<List<GreetingService>> { listOf(this.resolve(), this.resolve()) }
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
    fun arguments() = testDI {
        var expectedStringList = listOf("one", "two")

        dependencies {
            provide<GreetingService> { GreetingServiceImpl() }
            provide<List<String>>("my-strings") {
                expectedStringList
            }
            provide<List<Any>>("my-list") {
                listOf(
                    this.resolve<GreetingService>(),
                    this.resolve<List<String>>("my-strings"),
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
            var delegate = MapDependencyProvider()
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
            assertEquals(listOf(DependencyKey(typeInfo<GreetingService>())), assignmentKeys)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `custom reflection`() = testDI({
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
    fun `unnamed key mapping`() = testDI({
        provider {
            keyMapping = Unnamed
        }
    }) {
        dependencies {
            provide<GreetingService>("bank") { BankGreetingService() }
        }
        val named: GreetingService by dependencies.named("bank")
        val unnamed: GreetingService by dependencies
        assertEquals(HELLO_CUSTOMER, named.hello())
        assertEquals(HELLO_CUSTOMER, unnamed.hello())
    }

    // Use default DI configuration (not test mode)
    private fun testDI(
        pluginInstall: DependencyInjectionConfig.() -> Unit = {},
        block: Application.() -> Unit
    ) = testApplication {
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

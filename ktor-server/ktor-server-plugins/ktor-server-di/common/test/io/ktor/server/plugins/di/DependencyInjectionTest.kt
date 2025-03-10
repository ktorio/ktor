/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.testing.*
import io.ktor.util.reflect.typeInfo
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

internal const val HELLO = "Hello, world!"
private const val HELLO_CUSTOMER = "Hello, customer!"

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
    val greetingService: GreetingService,
    val bankService: BankService
) : GreetingService by greetingService, BankService by bankService

data class WorkExperience(val jobs: List<PaidWork>)

data class PaidWork(val requiredExperience: WorkExperience)

class DependencyInjectionTest {

    @Test
    fun missing() = testApplication {
        application {
            val failure = assertFailsWith<MissingDependencyException> {
                val service: GreetingService by dependencies
                fail("Should fail but found $service")
            }
            assertEquals("Could not resolve dependency for `${GreetingService::class.qualifiedName}`", failure.message)
        }
    }

    @Test
    fun `resolution out of order`() = testApplication {
        application {
            val failure = assertFailsWith<OutOfOrderDependencyException> {
                dependencies { provide<GreetingService> { GreetingServiceImpl() } }
                assertNotNull(dependencies.resolve<GreetingService>())
                dependencies { provide<String> { "Hello" } }
            }
            assertEquals("Attempted to define kotlin.String after dependencies were resolved", failure.message)
        }
    }

    @Test
    fun `conflicting declarations`() = testApplication {
        application {
            val failure = assertFailsWith<DuplicateDependencyException> {
                dependencies { provide<GreetingService> { GreetingServiceImpl() } }
                dependencies { provide<GreetingService> { BankGreetingService() } }
            }
            assertEquals("Attempted to redefine dependency `${GreetingService::class.qualifiedName}`", failure.message)
        }
    }

    @Test
    fun `circular dependencies`() = testApplication {
        application {
            val failure = assertFailsWith<CircularDependencyException> {
                dependencies {
                    provide<WorkExperience> { WorkExperience(resolve()) }
                    provide<PaidWork> { PaidWork(resolve()) }
                    provide<List<PaidWork>> { listOf(resolve()) }
                }
                val eligibleJobs: List<PaidWork> by dependencies
                fail("This should fail but returned $eligibleJobs")
            }
            assertEquals(
                "Circular dependency found for dependency `kotlin.collections.List<io.ktor.server.plugins.di.PaidWork>`",
                failure.message
            )
        }
    }

    @Test
    fun basic() = testApplication {
        application {
            dependencies {
                provide<GreetingService> { GreetingServiceImpl() }
            }

            val service: GreetingService by dependencies
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun caching() = testApplication {
        application {
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
    }

    @Test
    fun `basic with qualifier`() = testApplication {
        application {
            dependencies {
                provide<GreetingService>(name = "test") { GreetingServiceImpl() }
            }

            val service: GreetingService by dependencies.named("test")
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun lambdas() = testApplication {
        application {
            dependencies {
                provide<() -> GreetingService> { { GreetingServiceImpl() } }
            }

            val service: () -> GreetingService by dependencies
            assertEquals(HELLO, service().hello())
        }
    }

    @Test
    fun parameterized() = testApplication {
        application {
            dependencies {
                provide<List<GreetingService>> { listOf(GreetingServiceImpl()) }
            }

            val service: List<GreetingService> by dependencies
            assertEquals(HELLO, service.single().hello())
            assertFailsWith<MissingDependencyException> {
                dependencies.resolve<List<BankService>>()
            }
        }
    }

    @Test
    fun arguments() = testApplication {
        application {
            var expectedStringList = listOf("one", "two")

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
    fun `custom reflection`() = testApplication {
        install(DI) {
            reflection = object : DependencyReflection {
                override fun <T : Any> create(
                    kClass: KClass<T>,
                    init: (DependencyKey) -> Any
                ): T = when (kClass) {
                    GreetingService::class -> GreetingServiceImpl() as T
                    else -> fail("Unexpected class $kClass")
                }
            }
        }
        application {
            val service: GreetingService = dependencies.create()
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun `unnamed key mapping`() = testApplication {
        install(DI) {
            provider {
                keyMapping = Unnamed
            }
        }
        application {
            dependencies {
                provide<GreetingService>("bank") { BankGreetingService() }
            }
            val named: GreetingService by dependencies.named("bank")
            val unnamed: GreetingService by dependencies
            assertEquals(HELLO_CUSTOMER, named.hello())
            assertEquals(HELLO_CUSTOMER, unnamed.hello())
        }
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.testing.*
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class DependencyInjectionJvmTest {

    @Test
    fun `provide class reference`() = testApplication {
        application {
            dependencies {
                provide(GreetingServiceImpl::class)
            }

            val service: GreetingService by dependencies
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun `resolve class reference`() = testApplication {
        application {
            assertEquals(HELLO, dependencies.create<GreetingServiceImpl>().hello())
        }
    }

    @Test
    fun `constructor caching`() = testApplication {
        var calls = 0
        install(DI) {
            reflection = object : DependencyReflectionJvm() {
                override fun <T : Any> create(
                    kClass: KClass<T>,
                    init: (DependencyKey) -> Any
                ): T {
                    calls++
                    return super.create(kClass, init)
                }
            }
        }
        application {
            assertEquals(0, calls)
            repeat(10) {
                assertEquals(HELLO, dependencies.create<GreetingServiceImpl>().hello())
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `circular references from create`() = testApplication {
        application {
            val failure = assertFailsWith<CircularDependencyException> {
                dependencies {
                    provide<WorkExperience> { WorkExperience(resolve()) }
                    provide<PaidWork> { PaidWork(resolve()) }
                    provide<List<PaidWork>> { listOf(resolve()) }
                }
                val workExperience: WorkExperience = dependencies.create()
                fail("This should fail but returned $workExperience")
            }
            assertEquals(
                "Circular dependency found for dependency `io.ktor.server.plugins.di.WorkExperience`",
                failure.message
            )
        }
    }

    @Test
    fun `simple argument`() = testApplication {
        data class Guess(val number: Int)

        application {
            dependencies {
                provide { 42 }
            }

            val guess: Guess = dependencies.create()
            assertEquals(42, guess.number)
        }
    }

    @Test
    fun `multiple arguments`() = testApplication {
        application {
            dependencies {
                provide<BankService> { BankServiceImpl() }
                provide<GreetingService> { BankGreetingService() }
                provide(BankTeller::class)
            }
            val bankTeller: BankTeller by dependencies
            bankTeller.deposit(100)
            bankTeller.withdraw(50)
            assertEquals(50, bankTeller.balance())
            assertEquals("Hello, customer!", bankTeller.hello())
        }
    }

    @Test
    fun `covariant ambiguity`() = testApplication {
        application {
            dependencies {
                provide(GreetingServiceImpl::class)
                provide(BankGreetingService::class)
            }
            assertFailsWith<AmbiguousDependencyException> {
                val service: GreetingService by dependencies
                fail("Should fail but found $service")
            }
        }
    }

    @Test
    fun `parameterized covariant types`() = testApplication {
        var mySet = HashSet<String>()

        application {
            dependencies {
                provide { mySet }
            }
            assertEquals(mySet, dependencies.resolve())
            assertEquals(mySet, dependencies.resolve<Set<String>>())
            assertEquals(mySet, dependencies.resolve<Collection<String>>())
        }
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.IllegalCallableAccessException
import kotlin.reflect.jvm.javaMethod
import kotlin.test.*

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
            assertFailsWith<CircularDependencyException> {
                dependencies {
                    provide<WorkExperience> { WorkExperience(this.resolve()) }
                    provide<PaidWork> { PaidWork(this.resolve()) }
                    provide<List<PaidWork>> { listOf(this.resolve()) }
                }
                val workExperience: WorkExperience = dependencies.create()
                fail("This should fail but returned $workExperience")
            }
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
    fun `covariant ambiguity`() {
        assertFailsWith<AmbiguousDependencyException> {
            testApplication {
                install(DI) {
                    provider = MapDependencyProvider()
                }
                application {
                    dependencies {
                        provide(GreetingServiceImpl::class)
                        provide(BankGreetingService::class)
                    }
                    val service: GreetingService by dependencies
                    fail("Should fail but found $service")
                }
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

    @Test
    fun `install class from config`() {
        testConfigFile(GreetingServiceImpl::class.qualifiedName!!) {
            val service: GreetingService by dependencies
            assertEquals(HELLO, service.hello())
        }
    }

    /**
     * [KTOR-8322 Handle delegate pattern](https://youtrack.jetbrains.com/issue/KTOR-8322/Dependency-injection-handle-delegate-pattern)
     *     When declaring two classes that share an interface via delegation,
     *     we should resolve the ambiguity automatically.
     */
    @Ignore
    @Test
    fun `install class ref with args from config`() {
        testConfigFile(
            BankServiceImpl::class.qualifiedName!!,
            BankTeller::class.qualifiedName!!
        ) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref from config`() {
        testConfigFile(
            ::createGreetingService.qualifiedName,
            ::createBankService.qualifiedName,
        ) {
            val service: GreetingService by dependencies
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun `install function ref using resolver`() {
        testConfigFile(
            ::createGreetingService.qualifiedName,
            ::createBankService.qualifiedName,
            DependencyResolver::createBankTellerNoArgs.qualifiedName,
        ) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO, teller.hello())
        }
    }

    @Test
    fun `install function ref missing args`() {
        assertFails {
            testConfigFile(
                ::createBankTellerWithArgs.qualifiedName,
            ) {
                val teller: BankTeller by dependencies
                fail("Should fail but resolved $teller")
            }
        }
    }

    @Test
    fun `install from non-static functions`() {
        testConfigFile(
            BankModule::class.qualifiedName!!,
            BankModule::getBankServiceFromClass.qualifiedName,
        ) {
            val bank: BankService by dependencies
            assertEquals(0, bank.balance())
        }
    }

    @Test
    fun `install from private function fails`() {
        assertFailsWith<IllegalCallableAccessException> {
            testConfigFile(
                ::createBankTellerWithArgs.qualifiedName.replace(
                    ::createBankTellerWithArgs.name,
                    "getBankServicePrivately"
                ),
            ) {
                val bank: BankService by dependencies
                fail("Should fail but resolved $bank")
            }
        }
    }

    @Test
    fun `install function ref using args`() {
        testConfigFile(
            ::createBankService.qualifiedName,
            ::createBankTellerWithArgs.qualifiedName,
        ) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function refs out of order`() {
        testConfigFile(
            ::createBankTellerWithArgs.qualifiedName,
            ::createBankService.qualifiedName,
        ) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with install from module`() {
        testConfigFile(::createBankTellerWithArgs.qualifiedName,) {
            dependencies {
                provide<BankService> { BankServiceImpl() }
            }
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with special args`() {
        testConfigFile(
            ::createBankService.qualifiedName,
            ::createBankTellerWithLogging.qualifiedName,
        ) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with nullable args`() {
        testConfigFile(::createBankTellerWithNullables.qualifiedName) {
            val teller: BankTeller by dependencies
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `module parameters`() {
        testConfigFile(
            ::createGreetingService.qualifiedName,
            ::createBankService.qualifiedName,
            modules = listOf(
                Application::bankingModule.qualifiedName
            ),
            test = {
                assertEquals(HELLO, client.get("/hello").bodyAsText())
            }
        )
    }

    @Test
    fun `module parameters missing dependency`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            testConfigFile(
                ::createGreetingService.qualifiedName,
                modules = listOf(
                    Application::bankingModule.qualifiedName
                )
            )
        }
        assertIs<MissingDependencyException>(failure.cause?.cause)
    }

    private fun testConfigFile(
        vararg references: String,
        modules: List<String> = emptyList(),
        test: suspend ApplicationTestBuilder.() -> Unit = {},
        block: Application.() -> Unit = {}
    ) {
        testApplication {
            environment {
                config = MapApplicationConfig().apply {
                    put(
                        "ktor.application.dependencies",
                        listOf(*references)
                    )
                    if (modules.isNotEmpty()) {
                        put(
                            "ktor.application.modules",
                            modules
                        )
                    }
                }
            }
            application {
                block()
            }
            test()
        }
    }

    val KFunction<*>.qualifiedName: String get() =
        "${javaMethod?.declaringClass?.name}#${javaMethod?.name}"
}

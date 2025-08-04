/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import com.typesafe.config.ConfigFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.TestResult
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.test.*

interface Identifiable<ID> {
    val id: ID
}
interface Repository<E : Identifiable<ID>, ID> : ReadOnlyRepository<E, ID>
class ListRepository<E : Identifiable<ID>, ID>(
    val list: List<E>
) : Repository<E, ID> {
    override fun getAll(): List<E> = list
}
interface ReadOnlyRepository<out E : Identifiable<ID>, ID> {
    fun getAll(): List<E>
}
interface User : Identifiable<Long> {
    val name: String
}
data class FullUser(override val id: Long, override val name: String, val email: String) : User

class DependencyInjectionJvmTest {

    @Test
    fun `provide class reference`() = runTestDI {
        dependencies {
            provide(GreetingServiceImpl::class)
        }

        val service: GreetingService = dependencies.resolve()
        assertEquals(HELLO, service.hello())
    }

    @Test
    fun `resolve class reference`() = runTestDI {
        assertEquals(HELLO, dependencies.create<GreetingServiceImpl>().hello())
    }

    @Test
    fun `constructor caching`() {
        var calls = 0
        runTestDI({
            reflection = object : DependencyReflectionJvm() {
                override suspend fun <T : Any> create(
                    kClass: KClass<T>,
                    init: suspend (DependencyKey) -> Any
                ): T {
                    calls++
                    return super.create(kClass, init)
                }
            }
        }) {
            dependencies.provide(GreetingServiceImpl::class)
            assertEquals(0, calls)
            repeat(10) {
                assertEquals(HELLO, dependencies.resolve<GreetingServiceImpl>().hello())
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `circular references from create`() = runTestDI {
        dependencies {
            provide<WorkExperience> { WorkExperience(resolve()) }
            provide<PaidWork> { PaidWork(resolve()) }
            provide<List<PaidWork>> { listOf(resolve()) }
        }

        val failure = assertFailsWith<DependencyInjectionException> {
            dependencies.create<WorkExperience>()
        }
        assertIs<CircularDependencyException>(failure.cause)
    }

    @Test
    fun `simple argument`() = runTestDI {
        data class Guess(val number: Int)

        dependencies {
            provide { 42 }
        }

        val guess: Guess = dependencies.create()
        assertEquals(42, guess.number)
    }

    @Test
    fun `multiple arguments`() = runTestDI {
        dependencies {
            provide<BankService> { BankServiceImpl() }
            provide<GreetingService> { BankGreetingService() }
            provide(BankTeller::class)
        }
        val bankTeller: BankTeller = dependencies.resolve()
        bankTeller.deposit(100)
        bankTeller.withdraw(50)
        assertEquals(50, bankTeller.balance())
        assertEquals("Hello, customer!", bankTeller.hello())
    }

    @Test
    fun `covariant ambiguity`() {
        assertFailsWith<AmbiguousDependencyException> {
            testApplication {
                install(DI) {
                    // provider = MapDependencyProvider()
                }
                application {
                    dependencies {
                        provide(GreetingServiceImpl::class)
                        provide(BankGreetingService::class)
                    }
                    val service: GreetingService = dependencies.resolve()
                    fail("Should fail but found $service")
                }
            }
        }
    }

    @Test
    fun nullables() = runTestDI {
        dependencies {
            provide<BankTeller?> { null }
            provide<BankService> { BankServiceImpl() }
        }
        assertNull(dependencies.resolve<GreetingService?>())
        assertNull(dependencies.resolve<BankTeller?>())
        assertNotNull(dependencies.resolve<BankService?>(), "direct inference should be preferred")
    }

    @Test
    fun `parameterized covariant base types`() = runTestDI {
        val mySet = HashSet<String>()

        dependencies {
            provide { mySet }
        }
        assertEquals(mySet, dependencies.resolve())
        assertEquals(mySet, dependencies.resolve<Set<String>>())
        assertEquals(mySet, dependencies.resolve<Collection<String>>())
        assertNull(dependencies.resolve<List<String>?>())
    }

    @Test
    fun `parameterized covariant argument supertypes`() = runTestDI {
        val mySet = HashSet<String>()
        val myMap = mutableMapOf("hello" to GreetingServiceImpl())

        dependencies {
            provide { mySet }
            provide { myMap }
            provide<() -> String> { { mySet.iterator().next() } }
        }
        // `out` bounds should match supertypes
        assertEquals(mySet, dependencies.resolve<Collection<CharSequence>>())
        assertEquals(myMap, dependencies.resolve<Map<String, GreetingService>>())
        // return types in lambdas accept supertypes
        assertNotNull(dependencies.resolve<() -> CharSequence>())
        // strict bounds should not match
        assertNull(dependencies.resolve<MutableSet<CharSequence>?>())
    }

    @Test
    fun `covariant nullables`() = runTestDI {
        dependencies {
            provide<Int> { 123 }
        }
        assertEquals(123, dependencies.resolve<Number?>())
        assertEquals(null, dependencies.resolve<String?>())
    }

    @Test
    fun `install class from config`() {
        testConfigFile(GreetingServiceImpl::class.qualifiedName!!) {
            val service: GreetingService = dependencies.resolve()
            assertEquals(HELLO, service.hello())
        }
    }

    /**
     * [KTOR-8439 Handle type parameter subtype covariance](https://youtrack.jetbrains.com/issue/KTOR-8439/Dependency-injection-handle-type-parameter-subtype-covariance)
     *     We'll need to implement some resolution-side covariance handling for this kind of feature.
     */
    @Ignore
    @Test
    fun `parameterized covariant argument subtypes`() = runTestDI {
        val myChannel = Channel<CharSequence>()

        dependencies {
            provide { myChannel }
            provide<(CharSequence) -> Unit> { { myChannel.trySend(it) } }
        }
        // `in` bounds should match subtypes
        assertEquals(myChannel, dependencies.resolve<SendChannel<String>>())
        // parameters in lambdas accept subtypes
        assertNotNull(dependencies.resolve<(String) -> Boolean>())
    }

    @Test
    fun `install class ref with args from config`() {
        testConfigFile(
            BankServiceImpl::class.qualifiedName!!,
            BankTeller::class.qualifiedName!!
        ) {
            val teller: BankTeller = dependencies.resolve()
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref from config`() {
        testConfigFile(
            ::createGreetingService.qualifiedName,
            ::createBankService.qualifiedName,
        ) {
            val service: GreetingService = dependencies.resolve()
            assertEquals(HELLO, service.hello())
        }
    }

    @Test
    fun `install function ref using resolver`() {
        testConfigFile(
            ::createGreetingService.qualifiedName,
            ::createBankService.qualifiedName,
            DependencyRegistry::createBankTellerNoArgs.qualifiedName,
        ) {
            val teller: BankTeller = dependencies.resolve<BankTeller>()
            assertEquals(HELLO, teller.hello())
        }
    }

    @Test
    fun `install function ref missing args`() {
        assertFails {
            testConfigFile(
                ::createBankTellerWithArgs.qualifiedName,
            ) {
                val teller: BankTeller = dependencies.resolve()
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
            val bank: BankService = dependencies.resolve()
            assertEquals(0, bank.balance())
        }
    }

    @Test
    fun `install from private function fails`() {
        assertFailsWith<DependencyInjectionException> {
            testConfigFile(
                ::createBankTellerWithArgs.qualifiedName.replace(
                    ::createBankTellerWithArgs.name,
                    "getBankServicePrivately"
                ),
            ) {
                val bank: BankService = dependencies.resolve()
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
            val teller: BankTeller = dependencies.resolve()
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function refs out of order`() {
        testConfigFile(
            ::createBankTellerWithArgs.qualifiedName,
            ::createBankService.qualifiedName,
        ) {
            val teller: BankTeller = dependencies.resolve()
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with install from module`() {
        testConfigFile(::createBankTellerWithArgs.qualifiedName) {
            dependencies {
                provide<BankService> { BankServiceImpl() }
            }
            val teller: BankTeller = dependencies.resolve()
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with special args`() {
        testConfigFile(
            ::createBankService.qualifiedName,
            ::createBankTellerWithLogging.qualifiedName,
        ) {
            val teller: BankTeller = dependencies.resolve()
            assertEquals(HELLO_CUSTOMER, teller.hello())
        }
    }

    @Test
    fun `install function ref with nullable args`() {
        testConfigFile(::createBankTellerWithNullables.qualifiedName) {
            val teller: BankTeller = dependencies.resolve()
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
        assertFailsWith<IllegalArgumentException> {
            testConfigFile(
                ::createGreetingService.qualifiedName,
                modules = listOf(
                    Application::bankingModule.qualifiedName
                )
            )
        }
    }

    @Test
    fun `module parameters from properties`() {
        testConfigFile(
            FakeLogger::class.qualifiedName!!,
            ::dataSource.qualifiedName,
            modules = listOf(
                Application::restModule.qualifiedName
            ),
            extraConfig = """
                database {
                    url="jdbc:h2:mem:test"
                    username=admin
                    password=abc123
                }
            """.trimIndent(),
            test = {
                assertEquals(HttpStatusCode.OK, client.get("/data").status)
            }
        )
    }

    @Test
    fun `resolve flexible and nullable types`() = runTestDI {
        fun getString(): String? = "hello"

        dependencies {
            provide { System.out }
            provide { getString() }
        }
        val out: java.io.PrintStream = dependencies.resolve()
        val string: String = dependencies.resolve()
        assertEquals(System.out, out)
        assertEquals("hello", string)
    }

    @Test
    fun `resolve type parameter hierarchy with boundaries`() = runTestDI {
        val joey = FullUser(1L, "Joey", "joey.bloggs@joey.blog")
        dependencies {
            provide { ListRepository(listOf(joey)) }
        }

        assertEquals(
            listOf(joey),
            dependencies.resolve<ReadOnlyRepository<User, Long>>().getAll()
        )
        assertEquals(
            listOf(joey),
            dependencies.resolve<ReadOnlyRepository<Identifiable<Long>, Long>>().getAll()
        )
    }

    private fun runTestDI(
        pluginInstall: DependencyInjectionConfig.() -> Unit = {},
        block: suspend Application.() -> Unit
    ): TestResult = runTestWithRealTime {
        testDI(pluginInstall, block)
    }

    // Use default DI configuration (not test mode)
    private suspend fun testDI(
        pluginInstall: DependencyInjectionConfig.() -> Unit = {},
        block: suspend Application.() -> Unit
    ) = runTestApplication {
        install(DI) {
            pluginInstall()
//            if (!providerChanged) {
//                provider = MapDependencyProvider()
//            }
        }
        application {
            block()
        }
    }

    private fun testConfigFile(
        vararg references: String,
        modules: List<String> = emptyList(),
        extraConfig: String = "",
        test: suspend ApplicationTestBuilder.() -> Unit = {},
        block: suspend Application.() -> Unit = {}
    ) {
        val configText = """
            ktor {
                application {
                    startup=concurrent
                    dependencies=${references.map { "\"$it\"" }}
                    modules=${modules.map { "\"$it\"" }}
                }
            }
        """.trimIndent() + "\n$extraConfig"

        testApplication {
            environment {
                val parsed = ConfigFactory.parseString(configText)
                config = HoconApplicationConfig(parsed)
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

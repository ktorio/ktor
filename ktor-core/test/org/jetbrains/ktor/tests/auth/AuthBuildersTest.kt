package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class AuthBuildersTest {
    @Test
    fun testSimpleExtractAndVerify() {
        val got = arrayListOf<UserIdPrincipal>()

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        onSuccess { authContext, next ->
                            got.addAll(authContext.principals())
                            next(authContext)
                        }
                    }
                }
            }

            handleRequest {
                uri = "/"
            }

            assertEquals("name1", got.joinToString { it.name })
        }
    }

    @Test
    fun testSimpleExtractAndVerifyAll() {
        val got = arrayListOf<UserIdPrincipal>()
        var gotTestCredential = false

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        extractCredentials { TestCredential() }

                        verifyWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }
                        verifyAll { all -> if (all.filterIsInstance<TestCredential>().any()) gotTestCredential = true; emptyList() }

                        onSuccess { authContext, next ->
                            got.addAll(authContext.principals())
                            next(authContext)
                        }
                    }
                }
            }

            handleRequest {
                uri = "/"
            }

            assertEquals("name1", got.joinToString { it.name })
            assertTrue { gotTestCredential }
        }
    }

    @Test
    fun testPostVerify() {
        val got = arrayListOf<UserIdPrincipal>()

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        extractCredentials { UserPasswordCredential("name2", "ppp") }

                        verifyWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        postVerify { p: UserIdPrincipal -> p.name == "name1" }

                        onSuccess { authContext, next ->
                            got.addAll(authContext.principals())
                            next(authContext)
                        }
                    }
                }
            }

            handleRequest {
                uri = "/"
            }

            assertEquals("name1", got.joinToString { it.name })
        }
    }
}

private class TestCredential : Credential
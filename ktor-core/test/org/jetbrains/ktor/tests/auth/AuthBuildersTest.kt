package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class AuthBuildersTest {
    @Test
    fun testSimpleExtractAndVerifyBatch() {
        val got = arrayListOf<UserIdPrincipal>()

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        success { authContext, next ->
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
    fun testSimpleExtractAndVerify() {
        val got = arrayListOf<UserIdPrincipal>()

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyWith { credential: UserPasswordCredential -> UserIdPrincipal(credential.name) }

                        success { authContext, next ->
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
    fun testSimpleExtractAndVerifyFailed() {
        var failed = false

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyWith { credential: UserPasswordCredential -> null }

                        fail { next ->
                            failed = true
                            next()
                        }
                    }
                }
            }

            handleRequest {
                uri = "/"
            }

            assertTrue(failed, "auth should fail")
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

                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }
                        verifyBatchAll { all -> if (all.filterIsInstance<TestCredential>().any()) gotTestCredential = true; emptyList() }

                        success { authContext, next ->
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

                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        postVerify { p: UserIdPrincipal -> p.name == "name1" }

                        success { authContext, next ->
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
    fun testPrincipalsAccess() {
        val username = "testuser"

        withTestApplication {
            application.routing {
                route("/") {
                    auth {
                        formAuth()
                        verifyWith { c: UserPasswordCredential -> UserIdPrincipal(c.name) }

                        success { authContext, function ->
                            assertEquals(username, authContext.principal<UserIdPrincipal>()?.name)
                            ApplicationCallResult.Handled
                        }

                        fail {
                            fail("login failed")
                        }
                    }

                    handle {
                        assertEquals(username, authContext.principal<UserIdPrincipal>()?.name)
                        ApplicationCallResult.Handled
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/?user=$username&password=p")
        }
    }
}

private class TestCredential : Credential
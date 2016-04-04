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
                    authenticate {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        onSuccess {
                            got.addAll(call.authentication.principals())
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
                    authenticate {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyWith { credential: UserPasswordCredential -> UserIdPrincipal(credential.name) }

                        onSuccess {
                            got.addAll(call.authentication.principals())
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
                    authenticate {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        verifyWith { credential: UserPasswordCredential -> null }
                    }

                    handle {
                        failed = call.principals.isEmpty()
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
                    authenticate {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        extractCredentials { TestCredential() }

                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }
                        verifyBatchAll { all -> if (all.filterIsInstance<TestCredential>().any()) gotTestCredential = true; emptyList() }

                        onSuccess {
                            got.addAll(call.authentication.principals())
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
                    authenticate {
                        extractCredentials { UserPasswordCredential("name1", "ppp") }
                        extractCredentials { UserPasswordCredential("name2", "ppp") }

                        verifyBatchTypedWith { items: List<UserPasswordCredential> -> items.map { UserIdPrincipal(it.name) } }

                        postVerify { p: UserIdPrincipal -> p.name == "name1" }

                        onSuccess {
                            got.addAll(call.authentication.principals())
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
                    authentication {
                        formAuthentication { c -> UserIdPrincipal(c.name) }
                    }

                    handle {
                        assertEquals(username, call.authentication.principal<UserIdPrincipal>()?.name)
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/?user=$username&password=p")
        }
    }
}

private class TestCredential : Credential
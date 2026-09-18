/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth.ldap

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.auth.ldap.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.apache.directory.api.ldap.codec.api.*
import org.apache.directory.api.ldap.util.*
import java.net.*
import java.util.*
import javax.naming.Context
import javax.naming.directory.*
import javax.naming.ldap.*
import kotlin.test.*

// TODO unauthorized
@LDAPServerExtensionTest
@Ignore("LdapAuthTest is ignored because it is very slow. Run it explicitly when you need.")
class LdapAuthTest {

    @Test
    fun testLoginToServer(port: Int) = testApplication {
        install(Authentication) {
            basic {
                realm = "realm"
                validate { credential ->
                    ldapAuthenticate(credential, "ldap://$localhost:$port", "uid=%s,ou=system")
                }
            }
        }

        routing {
            authenticate {
                get("/") {
                    call.respondText(call.authentication.principal<UserIdPrincipal>()?.name ?: "null")
                }
            }
        }

        client.get("/").let { response ->
            response.headers[HttpHeaders.WWWAuthenticate].let {
                assertNotNull(it, "No auth challenge sent")
                val challenge = parseAuthorizationHeader(it)
                assertNotNull(challenge, "Challenge has incorrect format")
                assertEquals("Basic", challenge.authScheme)
                assertTrue(challenge is HttpAuthHeader.Parameterized, "It should be parameterized challenge")
                assertEquals("realm", challenge.parameter("realm"))
                assertEquals("UTF-8", challenge.parameter("charset"))
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("admin:secret".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("admin", response.bodyAsText())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("admin:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString(" \",; \u0419:pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
    }

    @Test
    fun testLoginToServerWithCustomPrincipal(port: Int) = testApplication {
        install(Authentication) {
            basic {
                realm = "realm"
                validate { credential ->
                    ldapAuthenticate(credential, "ldap://$localhost:$port", "uid=%s,ou=users,ou=system") {
                        val attributes = getAttributes("uid=${it.name},ou=users,ou=system")
                        LdapUserPrincipal(it.name, attributes.get("cn")?.get()?.toString() ?: "")
                    }
                }
            }
        }

        routing {
            authenticate {
                get("/") {
                    val principal = call.authentication.principal<LdapUserPrincipal>()
                    call.respondText(principal?.let { "${it.username}:${it.displayName}" } ?: "null")
                }
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:test".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-test:Test user", response.bodyAsText())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun testCustomLogin(port: Int) = testApplication {
        install(Authentication) {
            val ldapUrl = "ldap://$localhost:$port"
            val configure: (MutableMap<String, Any?>) -> Unit = { env ->
                env["java.naming.security.principal"] = "uid=admin,ou=system"
                env["java.naming.security.credentials"] = "secret"
                env["java.naming.security.authentication"] = "simple"
            }

            basic {
                validate { credential ->
                    ldapAuthenticate(credential, ldapUrl, configure) {
                        val users = (lookup("ou=system") as LdapContext).lookup("ou=users") as LdapContext
                        val controls = SearchControls().apply {
                            searchScope = SearchControls.ONELEVEL_SCOPE
                            returningAttributes = arrayOf("+", "*")
                        }

                        users.search("", "(uid=user-test)", controls).asSequence().firstOrNull {
                            val ldapPassword = (it.attributes.get("userPassword")?.get() as ByteArray?)
                                ?.toString(Charsets.ISO_8859_1)
                            ldapPassword == credential.password
                        }?.let { UserIdPrincipal(credential.name) }
                    }
                }
            }
        }

        routing {
            authenticate {
                get("/") {
                    call.respondText(call.authentication.principal<UserIdPrincipal>()?.name ?: "null")
                }
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:test".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-test", response.bodyAsText())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString(" \",; \u0419:pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
    }

    @Test
    fun testCustomCredentialAndPrincipalLogin(port: Int) = testApplication {
        install(Authentication) {
            val ldapUrl = "ldap://$localhost:$port"

            basic {
                validate { credential ->
                    val customCredential = LdapCredentials(credential.name, credential.password)
                    val configure: (MutableMap<String, Any?>) -> Unit = { env ->
                        env[Context.SECURITY_AUTHENTICATION] = "simple"
                        env[Context.SECURITY_PRINCIPAL] =
                            "uid=${ldapEscape(customCredential.username)},ou=users,ou=system"
                        env[Context.SECURITY_CREDENTIALS] = customCredential.password
                    }

                    ldapAuthenticate(customCredential, ldapUrl, configure) {
                        LdapUserPrincipal(it.username, "display-${it.username}")
                    }
                }
            }
        }

        routing {
            authenticate {
                get("/") {
                    val principal = call.authentication.principal<LdapUserPrincipal>()
                    call.respondText(principal?.let { "${it.username}:${it.displayName}" } ?: "null")
                }
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:test".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-test:display-user-test", response.bodyAsText())
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("user-test:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun testEnsureUser(port: Int, ldapCodecService: LdapApiService) {
        val env = Hashtable<String, String>()
        env["java.naming.factory.initial"] = "com.sun.jndi.ldap.LdapCtxFactory"
        env["java.naming.provider.url"] = "ldap://$localhost:$port"
        env["java.naming.security.principal"] = "uid=admin,ou=system"
        env["java.naming.security.credentials"] = "secret"
        env["java.naming.security.authentication"] = "simple"

        val ctx = (
            InitialLdapContext(
                env,
                JndiUtils.toJndiControls(ldapCodecService)
            ).lookup("ou=system") as LdapContext
            ).lookup("ou=users") as LdapContext

        val controls = SearchControls()
        controls.searchScope = SearchControls.ONELEVEL_SCOPE
        controls.returningAttributes = arrayOf("+", "*")
        val res = ctx.search("", "(ObjectClass=*)", controls).toList()

        assertEquals(listOf("user-test"), res.map { it.attributes.get("uid").get().toString() })
    }

    private val localhost: String
        get() =
            try {
                InetAddress.getLocalHost().hostAddress
            } catch (any: Throwable) {
                "127.0.0.1"
            }
}

private data class LdapUserPrincipal(
    val username: String,
    val displayName: String
)

private class LdapCredentials(val username: String, val password: String)

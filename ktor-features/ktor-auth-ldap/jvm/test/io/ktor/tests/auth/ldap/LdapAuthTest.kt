/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth.ldap

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.ldap.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.apache.directory.api.ldap.util.*
import org.apache.directory.server.annotations.*
import org.apache.directory.server.core.integ.*
import org.apache.directory.server.core.integ.IntegrationUtils.*
import org.apache.directory.server.ldap.*
import org.junit.runner.*
import java.net.*
import java.util.*
import javax.naming.directory.*
import javax.naming.ldap.*
import kotlin.test.*

@RunWith(FrameworkRunner::class)
@CreateLdapServer(transports = arrayOf(CreateTransport(protocol = "LDAP")))
@Ignore("LdapAuthTest is ignored because it is very slow. Run it explicitly when you need.")
class LdapAuthTest {
    @BeforeTest
    fun setUp() {
        // notice: it is just a test: never keep user password but message digest or hash with salt
        apply(
            ldapServer.directoryService,
            getUserAddLdif("uid=user-test,ou=users,ou=system", "test".toByteArray(), "Test user", "test")
        )
    }

    @Test
    fun testLoginToServer() {
        withTestApplication {
            application.install(Authentication) {
                basic {
                    realm = "realm"
                    validate { credential ->
                        ldapAuthenticate(credential, "ldap://$localhost:${ldapServer.port}", "uid=%s,ou=system")
                    }
                }
            }

            application.routing {
                authenticate {
                    get("/") {
                        call.respondText(call.authentication.principal<UserIdPrincipal>()?.name ?: "null")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                result.response.headers[HttpHeaders.WWWAuthenticate].let {
                    assertNotNull(it, "No auth challenge sent")
                    val challenge = parseAuthorizationHeader(it)
                    assertNotNull(challenge, "Challenge has incorrect format")
                    assertEquals("Basic", challenge.authScheme)
                    assertTrue(challenge is HttpAuthHeader.Parameterized, "It should be parameterized challenge")
                    assertEquals("realm", challenge.parameter("realm"))
                    assertEquals("UTF-8", challenge.parameter("charset"))
                }
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("admin:secret".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("admin", result.response.content)
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("admin:bad-pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertNull(result.response.content)
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertNull(result.response.content)
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString(" \",; \u0419:pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testCustomLogin() {
        withTestApplication {
            application.install(Authentication) {
                val ldapUrl = "ldap://$localhost:${ldapServer.port}"
                val configure: (MutableMap<String, Any?>) -> Unit = { env ->
                    env.put("java.naming.security.principal", "uid=admin,ou=system")
                    env.put("java.naming.security.credentials", "secret")
                    env.put("java.naming.security.authentication", "simple")
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

            application.routing {
                authenticate {
                    get("/") {
                        call.respondText(call.authentication.principal<UserIdPrincipal>()?.name ?: "null")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("user-test:test".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("user-test", result.response.content)
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("user-test:bad-pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString("bad-user:bad-pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString(" \",; \u0419:pass".toByteArray())
                )
            }.let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testEnsureUser() {
        val env = Hashtable<String, String>()
        env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory")
        env.put("java.naming.provider.url", "ldap://$localhost:${ldapServer.port}")
        env.put("java.naming.security.principal", "uid=admin,ou=system")
        env.put("java.naming.security.credentials", "secret")
        env.put("java.naming.security.authentication", "simple")

        val ctx = (
            InitialLdapContext(
                env,
                JndiUtils.toJndiControls(ldapServer.directoryService.ldapCodecService)
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

    companion object {
        @JvmStatic
        lateinit var ldapServer: LdapServer
    }
}

package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.security.*
import kotlin.test.*

class DigestTest {
    @Test
    fun createExampleChallengeFromRFC() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Infrastructure) { call ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.digestAuthChallenge(
                        realm = "testrealm@host.com",
                        nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
                        opaque = "5ccc069c403ebaf9f0171e9517f40e41"
                )))
            }

            val response = handleRequest {
            }

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
            assertEquals("""Digest
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41",
                 algorithm="MD5" """.normalize(), response.response.headers[HttpHeaders.WWWAuthenticate])
        }
    }

    @Test
    fun testParseDigestExampleFromRFC() {
        withTestApplication {
            val foundDigests = arrayListOf<DigestCredential>()

            application.routing {
                route("/") {
                    authentication {
                        intercept(Authentication.Pipeline.RequestAuthentication) {
                            subject.call.extractDigest()?.let { digest ->
                                foundDigests.add(digest)
                            }
                        }
                    }

                    handle {}
                }
            }

            handleRequest {
                uri = "/"

                addHeader(HttpHeaders.Authorization, """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize())
            }

            assertEquals(1, foundDigests.size)

            val theOnlyDigest = foundDigests.single()

            assertEquals("testrealm@host.com", theOnlyDigest.realm)
            assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", theOnlyDigest.nonce)
            assertEquals("/dir/index.html", theOnlyDigest.digestUri)
            assertEquals("00000001", theOnlyDigest.nonceCount)
            assertEquals("6629fae49393a05397450978507c4ef1", theOnlyDigest.response)
            assertEquals("5ccc069c403ebaf9f0171e9517f40e41", theOnlyDigest.opaque)
        }
    }

    @Test
    fun testVerify() {
        val authHeaderContent = """Digest username="Mufasa",
                     realm="testrealm@host.com",
                     nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                     uri="/dir/index.html",
                     qop=auth,
                     nc=00000001,
                     cnonce="0a4f113b",
                     response="6629fae49393a05397450978507c4ef1",
                     opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()

        val authHeader = parseAuthorizationHeader(authHeaderContent) as HttpAuthHeader.Parameterized
        val digest = authHeader.toDigestCredential()

        val p = "Circle Of Life"
        val userNameRealmPassword = "${digest.userName}:${digest.realm}:$p"
        val digester = MessageDigest.getInstance(digest.algorithm ?: "MD5")

        assertEquals(digest.response, digest.expectedDigest(HttpMethod.Get, digester, digest(digester, userNameRealmPassword)))
        assertTrue(digest.verify(HttpMethod.Get, digester) { user, realm -> digest(digester, "$user:$realm:$p") })
    }

    private fun Application.configureDigestServer() {
        routing {
            route("/") {
                authentication {
                    val p = "Circle Of Life"
                    val digester = MessageDigest.getInstance("MD5")
                    digestAuthentication { userName, realm -> digest(digester, "$userName:$realm:$p") }
                }

                handle {
                    call.respondText("Secret info")
                }
            }
        }
    }

    @Test
    fun testDigestFromRFCExample() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(HttpHeaders.Authorization, """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize())
            }

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertEquals("Secret info", response.response.content)
        }
    }

    @Test
    fun testDigestFromRFCExampleAuthFailed() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(HttpHeaders.Authorization, """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="bad response goes here  507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize())
            }

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
        }
    }

    private fun digest(digester: MessageDigest, data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }

    private fun String.normalize() = lineSequence().map { it.trim() }.joinToString(" ")
}
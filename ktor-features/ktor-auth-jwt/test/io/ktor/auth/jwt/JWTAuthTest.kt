package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.nhaarman.mockito_kotlin.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.security.*
import java.security.interfaces.*
import java.util.concurrent.*
import kotlin.test.*

class JWTAuthTest {

    @Test
    fun testJwtNoAuth() {
        withApplication {
            application.configureServerJwt()

            val response = handleRequest {
                uri = "/"
            }

            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtSuccess() {
        withApplication {
            application.configureServerJwt()

            val token = getToken()

            val response = handleRequestWithToken(token)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertNotNull(response.response.content)
        }
    }

    @Test
    fun testJwtSuccessWithCustomScheme() {
        withApplication {
            application.configureServerJwt {
                authSchemes("Bearer", "Token")
            }

            val token = getToken(scheme = "Token")

            val response = handleRequestWithToken(token)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertNotNull(response.response.content)
        }
    }

    @Test
    fun testJwtSuccessWithCustomSchemeWithDifferentCases() {
        withApplication {
            application.configureServerJwt {
                authSchemes("Bearer", "tokEN")
            }

            val token = getToken(scheme = "TOKen")

            val response = handleRequestWithToken(token)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertNotNull(response.response.content)
        }
    }

    @Test
    fun testJwtAlgorithmMismatch() {
        withApplication {
            application.configureServerJwt()
            val token = JWT.create().withAudience(audience).withIssuer(issuer).sign(Algorithm.HMAC256("false"))
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtAudienceMismatch() {
        withApplication {
            application.configureServerJwt()
            val token = JWT.create().withAudience("wrong").withIssuer(issuer).sign(algorithm)
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtIssuerMismatch() {
        withApplication {
            application.configureServerJwt()
            val token = JWT.create().withAudience(audience).withIssuer("wrong").sign(algorithm)
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkNoAuth() {
        withApplication {
            application.configureServerJwk()

            val response = handleRequest {
                uri = "/"
            }

            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkSuccess() {
        withApplication {
            application.configureServerJwk(mock = true)

            val token = getJwkToken()

            val response = handleRequestWithToken(token)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertNotNull(response.response.content)
        }
    }

    @Test
    fun testJwkSuccessNoIssuer() {
        withApplication {
            application.configureServerJwkNoIssuer(mock = true)

            val token = getJwkToken()

            val response = handleRequestWithToken(token)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertNotNull(response.response.content)
        }
    }

    @Test
    fun testJwtAuthSchemeMismatch() {
        withApplication {
            application.configureServerJwt()
            val token = getToken().removePrefix("Bearer ")
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtAuthSchemeMismatch2() {
        withApplication {
            application.configureServerJwt()
            val token = getToken("Token")
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtAuthSchemeMistake() {
        withApplication {
            application.configureServerJwt()
            val token = getToken().replace("Bearer", "Bearer:")
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwtBlobPatternMismatch() {
        withApplication {
            application.configureServerJwt()
            val token = getToken().let {
                val i = it.length - 2
                it.replaceRange(i..i + 1, " ")
            }
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkAuthSchemeMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = getJwkToken(false)
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkAuthSchemeMistake() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = getJwkToken(true).replace("Bearer", "Bearer:")
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkBlobPatternMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = getJwkToken(true).let {
                val i = it.length - 2
                it.replaceRange(i..i + 1, " ")
            }
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkAlgorithmMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = JWT.create().withAudience(audience).withIssuer(issuer).sign(Algorithm.HMAC256("false"))
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkAudienceMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = JWT.create().withAudience("wrong").withIssuer(issuer).sign(algorithm)
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkIssuerMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = JWT.create().withAudience(audience).withIssuer("wrong").sign(algorithm)
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkKidMismatch() {
        withApplication {
            application.configureServerJwk(mock = true)

            val token = "Bearer " + JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withKeyId("wrong")
                .sign(jwkAlgorithm)

            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun testJwkInvalidToken() {
        withApplication {
            application.configureServerJwk(mock = true)
            val token = "Bearer wrong"
            val response = handleRequestWithToken(token)
            verifyResponseUnauthorized(response)
        }
    }

    @Test
    fun verifyWithMock() {
        val token = getJwkToken(prefix = false)
        val provider = getJwkProviderMock()
        val kid = JWT.decode(token).keyId
        val jwk = provider.get(kid)
        val algorithm = jwk.makeAlgorithm()
        val verifier = JWT.require(algorithm).withIssuer(issuer).build()
        verifier.verify(token)
    }

    @Test
    fun verifyNullAlgorithmWithMock() {
        val token = getJwkToken(prefix = false)
        val provider = getJwkProviderNullAlgorithmMock()
        val kid = JWT.decode(token).keyId
        val jwk = provider.get(kid)
        val algorithm = jwk.makeAlgorithm()
        val verifier = JWT.require(algorithm).withIssuer(issuer).build()
        verifier.verify(token)
    }

    private fun verifyResponseUnauthorized(response: TestApplicationCall) {
        assertTrue(response.requestHandled)
        assertEquals(HttpStatusCode.Unauthorized, response.response.status())
        assertNull(response.response.content)
    }

    private fun TestApplicationEngine.handleRequestWithToken(token: String): TestApplicationCall {
        return handleRequest {
            uri = "/"
            addHeader(HttpHeaders.Authorization, token)
        }
    }

    private fun Application.configureServerJwk(mock: Boolean = false) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            verifier(if (mock) getJwkProviderMock() else makeJwkProvider(), issuer)
            validate { credential ->
                when {
                    credential.payload.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }

        }
    }

    private fun Application.configureServerJwkNoIssuer(mock: Boolean = false) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            verifier(if (mock) getJwkProviderMock() else makeJwkProvider())
            validate { credential ->
                when {
                    credential.payload.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }

        }
    }

    private fun Application.configureServerJwt(extra: JWTAuthenticationProvider.() -> Unit = {}) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            verifier(makeJwtVerifier())
            validate { credential ->
                when {
                    credential.payload.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }
            extra()
        }
    }

    private fun Application.configureServer(authBlock: (Authentication.Configuration.() -> Unit)) {
        install(Authentication) {
            authBlock(this)
        }
        routing {
            authenticate {
                get("/") {
                    val principal = call.authentication.principal<JWTPrincipal>()!!
                    principal.payload
                    //val subjectString = principal.payload.subject.removePrefix("auth0|")
                    //println(subjectString)
                    call.respondText("Secret info")
                }
            }
        }
    }

    private val algorithm = Algorithm.HMAC256("secret")
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048, SecureRandom())
    }.generateKeyPair()
    private val jwkAlgorithm = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
    private val issuer = "https://jwt-provider-domain/"
    private val audience = "jwt-audience"
    private val realm = "ktor jwt auth test"

    private fun makeJwkProvider(): JwkProvider = JwkProviderBuilder(issuer)
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    private fun makeJwtVerifier(): JWTVerifier = JWT
            .require(algorithm)
            .withAudience(audience)
            .withIssuer(issuer)
            .build()

    private val kid = "NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg"

    private fun getJwkProviderNullAlgorithmMock(): JwkProvider {
        val jwk = mock<Jwk> {
            on { publicKey } doReturn keyPair.public
        }
        return mock {
            on { get(kid) } doReturn jwk
        }
    }

    private fun getJwkProviderMock(): JwkProvider {
        val jwk = mock<Jwk> {
            on { algorithm } doReturn jwkAlgorithm.name
            on { publicKey } doReturn keyPair.public
        }
        return mock {
            on { get(kid) } doReturn jwk
            on { get("wrong") } doThrow(SigningKeyNotFoundException("Key not found", null))
        }
    }

    private fun getJwkToken(prefix: Boolean = true) = (if (prefix) "Bearer " else "") + JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withKeyId(kid)
            .sign(jwkAlgorithm)

    private fun getToken(scheme: String = "Bearer") = "$scheme " + JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .sign(algorithm)

}

package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.request.*
import io.ktor.response.*
import java.security.interfaces.*
import java.util.*

private val JWTAuthKey: Any = "JWTAuth"

class JWTCredential(val payload: Payload) : Credential
class JWTPrincipal(val payload: Payload) : Principal

class JWTAuthenticationProvider(name: String?) : AuthenticationProvider(name) {
    internal var authenticationFunction: suspend ApplicationCall.(JWTCredential) -> Principal? = { null }

    internal var supportedAuthSchemes = setOf("Bearer")
    var realm: String = "Ktor Server"
    internal var verifier: ((HttpAuthHeader?) -> JWTVerifier?) = { null }

    /**
     * @param [schemes] list of supported authentication schemes for JWT (by default only "Bearer")
     */
    fun authSchemes(vararg schemes: String = arrayOf("Bearer")) {
        require(schemes.isNotEmpty()) { "At least one scheme should be provided" }
        supportedAuthSchemes = schemes.toSet()
    }

    /**
     * @param [verifier] verifies token format and signature
     */
    fun verifier(verifier: JWTVerifier) {
        this.verifier = { verifier }
    }

    /**
     * @param [verifier] verifies token format and signature
     */
    fun verifier(verifier: (HttpAuthHeader?) -> JWTVerifier?) {
        this.verifier = verifier
    }

    /**
     * @param [jwkProvider] provides the JSON Web Key
     * @param [issuer] the issuer of the JSON Web Token
     */
    fun verifier(jwkProvider: JwkProvider, issuer: String) {
        this.verifier = { token -> getVerifier(jwkProvider, issuer, token, supportedAuthSchemes) }
    }

    fun validate(body: suspend ApplicationCall.(JWTCredential) -> Principal?) {
        authenticationFunction = body
    }
}

/**
 * Installs JWT Authentication mechanism
 */
fun Authentication.Configuration.jwt(name: String? = null, configure: JWTAuthenticationProvider.() -> Unit) {
    val provider = JWTAuthenticationProvider(name).apply(configure)
    val realm = provider.realm
    val authenticate = provider.authenticationFunction
    val verifier = provider.verifier
    val supportedAuthSchemes = provider.supportedAuthSchemes
    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.request.parseAuthorizationHeaderOrNull()
        val principal = verifyAndValidate(call, verifier(token), token, supportedAuthSchemes, authenticate)
        evaluate(token, principal, realm, supportedAuthSchemes, context)
    }
    register(provider)
}

private suspend fun evaluate(token: HttpAuthHeader?, principal: Principal?, realm: String, supportedAuthSchemes: Set<String>, context: AuthenticationContext) {
    val cause = when {
        token == null -> AuthenticationFailedCause.NoCredentials
        principal == null -> AuthenticationFailedCause.InvalidCredentials
        else -> null
    }
    if (cause != null) {
        context.challenge(JWTAuthKey, cause) {
            call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(realm, supportedAuthSchemes)))
            it.complete()
        }
    }
    if (principal != null) {
        context.principal(principal)
    }
}

private fun getVerifier(jwkProvider: JwkProvider, issuer: String, token: HttpAuthHeader?, supportedAuthSchemes: Set<String>): JWTVerifier? {
    val jwk = token.getBlob(supportedAuthSchemes)?.let { jwkProvider.get(JWT.decode(it).keyId) }

    val algorithm = try {
        jwk?.makeAlgorithm()
    } catch (ex: IllegalArgumentException) {
        null
    } ?: return null
    return JWT.require(algorithm).withIssuer(issuer).build()
}

private suspend fun verifyAndValidate(call: ApplicationCall, jwtVerifier: JWTVerifier?, token: HttpAuthHeader?, supportedAuthSchemes: Set<String>, validate: suspend ApplicationCall.(JWTCredential) -> Principal?): Principal? {
    val jwt = try {
        token.getBlob(supportedAuthSchemes)?.let { jwtVerifier?.verify(it) }
    } catch (ex: JWTVerificationException) {
        null
    } ?: return null

    val payload = jwt.parsePayload()
    val credentials = JWTCredential(payload)
    return validate(call, credentials)
}

private fun HttpAuthHeader?.getBlob(supportedAuthSchemes: Set<String>) = when {
    this is HttpAuthHeader.Single && authScheme in supportedAuthSchemes -> blob
    else -> null
}

private fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (ex: IllegalArgumentException) {
    null
}

private fun HttpAuthHeader.Companion.bearerAuthChallenge(realm: String, supportedAuthSchemes: Set<String>): HttpAuthHeader =
        HttpAuthHeader.Parameterized(supportedAuthSchemes.first(), mapOf(HttpAuthHeader.Parameters.Realm to realm))

private fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
}

private fun DecodedJWT.parsePayload(): Payload {
    val payloadString = String(Base64.getUrlDecoder().decode(payload))
    return JWTParser().parsePayload(payloadString)
}

@Deprecated("Use DSL builder form", replaceWith = ReplaceWith("jwt {\n" +
        "        this.realm = realm\n" +
        "        this.verifier(jwtVerifier)\n" +
        "        this.validate(validate)\n" +
        "    }\n"))
fun Authentication.Configuration.jwtAuthentication(jwtVerifier: JWTVerifier, realm: String, validate: suspend (JWTCredential) -> Principal?) {
    jwt {
        this.realm = realm
        this.verifier(jwtVerifier)
        this.validate { validate(it) }
    }
}

@Deprecated("Use DSL builder form", replaceWith = ReplaceWith("jwt {\n" +
        "        this.realm = realm\n" +
        "        this.verifier(jwkProvider, issuer)\n" +
        "        this.validate(validate)\n" +
        "    }\n"))
fun Authentication.Configuration.jwtAuthentication(jwkProvider: JwkProvider, issuer: String, realm: String, validate: suspend (JWTCredential) -> Principal?) {
    jwt {
        this.realm = realm
        this.verifier(jwkProvider, issuer)
        this.validate { validate(it) }
    }
}

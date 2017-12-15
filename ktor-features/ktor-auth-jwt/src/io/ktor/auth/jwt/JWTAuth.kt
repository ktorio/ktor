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

/**
 * Add JWT token authentication to the pipeline using a [JWTVerifier] to verify the token integrity.
 * @param [jwtVerifier] verifies token format and signature
 * @param [realm] used in the WWW-Authenticate response header
 * @param [validate] verify the credentials provided by the client token
 */
fun AuthenticationPipeline.jwtAuthentication(jwtVerifier: JWTVerifier, realm: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.request.parseAuthorizationHeaderOrNull()
        val principal = verifyAndValidate(jwtVerifier, token, validate)
        evaluate(token, principal, realm, context)
    }
}

/**
 * Add JWT token authentication to the pipeline using a [JwkProvider] to verify the token integrity.
 * @param [jwkProvider] provides the JSON Web Key
 * @param [issuer] the issuer of the JSON Web Token
 * @param [realm] used in the WWW-Authenticate response header
 */
fun AuthenticationPipeline.jwtAuthentication(jwkProvider: JwkProvider, issuer: String, realm: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.request.parseAuthorizationHeaderOrNull()
        val verifier = getVerifier(jwkProvider, issuer, token)
        val principal = verifyAndValidate(verifier, token, validate)
        evaluate(token, principal, realm, context)
    }
}

private suspend fun evaluate(token: HttpAuthHeader?, principal: Principal?, realm: String, context: AuthenticationContext) {
    val cause = when {
        token == null -> AuthenticationFailedCause.NoCredentials
        principal == null -> AuthenticationFailedCause.InvalidCredentials
        else -> null
    }
    if (cause != null) {
        context.challenge(JWTAuthKey, cause) {
            call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(realm)))
            it.complete()
        }
    }
    if (principal != null) {
        context.principal(principal)
    }
}

private fun getVerifier(jwkProvider: JwkProvider, issuer: String, token: HttpAuthHeader?): JWTVerifier? {
    val jwk = token.getBlob()?.let { jwkProvider.get(JWT.decode(it).keyId) }

    val algorithm = try {
        jwk?.makeAlgorithm()
    } catch (ex: IllegalArgumentException) {
        null
    } ?: return null
    return JWT.require(algorithm).withIssuer(issuer).build()
}

private fun verifyAndValidate(jwtVerifier: JWTVerifier?, token: HttpAuthHeader?, validate: (JWTCredential) -> Principal?): Principal? {
    val jwt = try {
        token.getBlob()?.let { jwtVerifier?.verify(it) }
    } catch (ex: JWTVerificationException) {
        null
    } ?: return null

    val payload = jwt.parsePayload()
    val credentials = payload.let(::JWTCredential)
    return credentials.let(validate)
}

private fun HttpAuthHeader?.getBlob() = when {
    this is HttpAuthHeader.Single && authScheme == "Bearer" -> blob
    else -> null
}

private fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (ex: IllegalArgumentException) {
    null
}

private fun HttpAuthHeader.Companion.bearerAuthChallenge(realm: String): HttpAuthHeader =
        HttpAuthHeader.Parameterized("Bearer", mapOf(HttpAuthHeader.Parameters.Realm to realm))

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

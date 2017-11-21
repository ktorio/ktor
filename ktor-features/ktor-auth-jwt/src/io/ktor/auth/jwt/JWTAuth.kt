package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.response.*
import java.security.interfaces.*
import java.util.*

private val JWTAuthKey: Any = "JWTAuth"

data class JWTCredential(val payload: Payload) : Credential
data class JWTPrincipal(val payload: Payload) : Principal

fun AuthenticationPipeline.jwtAuthentication(verifier: JWTVerifier, realm: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.request.parseAuthorizationHeader()
        val principal = verifyAndValidate(verifier, token, validate)
        evaluate(token, principal, realm, context)
    }
}

fun AuthenticationPipeline.jwtAuthentication(jwkProvider: JwkProvider, issuer: String, realm: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.request.parseAuthorizationHeader()
        val verifier = getVerifier(jwkProvider, issuer, token)
        val principal = verifyAndValidate(verifier, token, validate)
        evaluate(token, principal, realm, context)
    }
}

private suspend fun evaluate(token: HttpAuthHeader?, principal: Principal?, realm: String, context: AuthenticationContext) {
    val cause = when {
        token == null -> NotAuthenticatedCause.NoCredentials
        principal == null -> NotAuthenticatedCause.InvalidCredentials
        else -> null
    }
    if (cause != null) {
        context.challenge(JWTAuthKey, cause) {
            call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(realm)))
            it.success()
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

private fun verifyAndValidate(verifier: JWTVerifier?, token: HttpAuthHeader?, validate: (JWTCredential) -> Principal?): Principal? {
    val jwt = try {
        token.getBlob()?.let { verifier?.verify(it) }
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

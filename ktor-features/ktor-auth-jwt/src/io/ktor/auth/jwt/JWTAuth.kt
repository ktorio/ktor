package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import java.security.interfaces.*
import java.util.*

private val JWTAuthKey: Any = "JWTAuth"

data class JWTCredential(val payload: Payload) : Credential
data class JWTPrincipal(val payload: Payload) : Principal

fun AuthenticationPipeline.jwtAuthentication(verifier: JWTVerifier, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.getAuthToken()
        val principal = verifyAndValidate(verifier, token, validate)
        evaluate(token, principal, context)
    }
}

fun AuthenticationPipeline.jwtAuthentication(jwkProvider: JwkProvider, issuer: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = call.getAuthToken()
        val verifier = getVerifier(jwkProvider, issuer, token)
        val principal = verifyAndValidate(verifier, token, validate)
        evaluate(token, principal, context)
    }
}

private suspend fun evaluate(token: String?, principal: Principal?, context: AuthenticationContext) {
    val cause = when {
        token == null -> NotAuthenticatedCause.NoCredentials
        principal == null -> NotAuthenticatedCause.InvalidCredentials
        else -> null
    }
    if (cause != null) {
        context.challenge(JWTAuthKey, cause) {
            call.respond(HttpStatusCode.Unauthorized)
            it.success()
        }
    }
    if (principal != null) {
        context.principal(principal)
    }
}

private fun getVerifier(jwkProvider: JwkProvider, issuer: String, token: String?): JWTVerifier? {
    val jwk = token?.let { jwkProvider.get(JWT.decode(it).keyId) }

    val algorithm = try {
        jwk?.makeAlgorithm()
    } catch (ex: IllegalArgumentException) {
        null
    } ?: return null
    return JWT.require(algorithm).withIssuer(issuer).build()
}

private fun verifyAndValidate(verifier: JWTVerifier?, token: String?, validate: (JWTCredential) -> Principal?): Principal? {
    val jwt = try {
        token?.let { verifier?.verify(it) }
    } catch (ex: JWTVerificationException) {
        null
    } ?: return null

    val payload = jwt.parsePayload()
    val credentials = payload.let(::JWTCredential)
    return credentials.let(validate)
}

private fun ApplicationCall.getAuthToken(): String? {
    val headers = request.headers
    val authHeader = headers[HttpHeaders.Authorization] ?: return null
    val token = authHeader.removePrefix("Bearer ")
    return token
}

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
    val parsedPayload = JWTParser().parsePayload(payloadString)
    return parsedPayload
}

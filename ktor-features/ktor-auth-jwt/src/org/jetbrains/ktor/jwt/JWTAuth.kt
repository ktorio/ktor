package org.jetbrains.ktor.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.respond
import java.security.interfaces.*
import java.util.*

val JWTAuthKey: Any = "JWTAuth"

data class JWTCredential(val payload: Payload): Credential
data class JWTPrincipal(val payload: Payload): Principal

fun AuthenticationPipeline.jwtAuthentication(verifier: JWTVerifier, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        try {
            val token = call.getAuthToken()

            val jwt = verifier.verify(token)
            val payload = jwt.parsePayload()

            val credentials = JWTCredential(payload)
            val principal = requireNotNull(credentials.let(validate))
            context.principal(principal)
        } catch(e: Exception) {
            context.challenge(JWTAuthKey, NotAuthenticatedCause.InvalidCredentials) {
                it.success()
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

fun AuthenticationPipeline.jwtAuthentication(jwkProvider: JwkProvider, issuer: String, validate: (JWTCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        try {
            val token = call.getAuthToken()

            val jwk = jwkProvider.get(JWT.decode(token).keyId)

            val algorithm = jwk.makeAlgorithm()
            val verifier = JWT.require(algorithm).withIssuer(issuer).build()

            val jwt = verifier.verify(token)
            val payload = jwt.parsePayload()

            val credentials = JWTCredential(payload)
            val principal = requireNotNull(credentials.let(validate))
            context.principal(principal)
        } catch(e: Exception) {
            context.challenge(JWTAuthKey, NotAuthenticatedCause.InvalidCredentials) {
                it.success()
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

private fun ApplicationCall.getAuthToken(): String {
    val headers = request.headers
    val authHeader = requireNotNull(headers[HttpHeaders.Authorization])
    val token = authHeader.removePrefix("Bearer ")
    return token
}

private fun Jwk.makeAlgorithm(): Algorithm {
    return when(algorithm) {
        "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
        "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
        "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
        "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
        else -> throw IllegalArgumentException("unsupported algorithm $algorithm")
    }
}

private fun DecodedJWT.parsePayload(): Payload {
    val payloadString = String(Base64.getUrlDecoder().decode(payload))
    val parsedPayload = JWTParser().parsePayload(payloadString)
    return parsedPayload
}

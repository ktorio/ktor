package org.jetbrains.ktor.jwt

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.impl.JWTParser
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import org.jetbrains.ktor.application.ApplicationCall
import org.jetbrains.ktor.auth.AuthenticationPipeline
import org.jetbrains.ktor.auth.Credential
import org.jetbrains.ktor.auth.NotAuthenticatedCause
import org.jetbrains.ktor.auth.Principal
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.response.respond
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
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

            val credentials = org.jetbrains.ktor.jwt.JWTCredential(payload)
            val principal = requireNotNull(credentials.let(validate))
            context.principal(principal)
        } catch(e: Exception) {
            context.challenge(org.jetbrains.ktor.jwt.JWTAuthKey, NotAuthenticatedCause.InvalidCredentials) {
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

            val credentials = org.jetbrains.ktor.jwt.JWTCredential(payload)
            val principal = requireNotNull(credentials.let(validate))
            context.principal(principal)
        } catch(e: Exception) {
            context.challenge(org.jetbrains.ktor.jwt.JWTAuthKey, NotAuthenticatedCause.InvalidCredentials) {
                it.success()
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

private fun ApplicationCall.getAuthToken(): String {
    val headers = request.headers
    val authHeader = requireNotNull(headers["Authorization"])
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

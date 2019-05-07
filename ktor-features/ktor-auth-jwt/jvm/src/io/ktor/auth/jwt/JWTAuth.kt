/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.auth.*
import io.ktor.request.*
import io.ktor.response.*
import org.slf4j.*
import java.security.interfaces.*
import java.util.*

private val JWTAuthKey: Any = "JWTAuth"

private val JWTLogger: Logger = LoggerFactory.getLogger("io.ktor.auth.jwt")

/**
 * Represents a JWT credential consist of the specified [payload]
 * @param payload JWT
 * @see Payload
 */
class JWTCredential(val payload: Payload) : Credential

/**
 * Represents a JWT principal consist of the specified [payload]
 * @param payload JWT
 * @see Payload
 */
class JWTPrincipal(val payload: Payload) : Principal

/**
 * JWT authentication provider that will be registered with the specified [name]
 */
class JWTAuthenticationProvider internal constructor(config: Configuration) : AuthenticationProvider(config) {

    internal val realm: String = config.realm
    internal val schemes: JWTAuthSchemes = config.schemes
    internal val authHeader: (ApplicationCall) -> HttpAuthHeader? = config.authHeader
    internal val verifier: ((HttpAuthHeader) -> JWTVerifier?) = config.verifier
    internal val authenticationFunction = config.authenticationFunction

    /**
     * JWT auth provider configuration
     */
    class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        internal var authenticationFunction: AuthenticationFunction<JWTCredential> = { null }

        internal var schemes = JWTAuthSchemes("Bearer")

        internal var authHeader: (ApplicationCall) -> HttpAuthHeader? =
            { call -> call.request.parseAuthorizationHeaderOrNull() }

        internal var verifier: ((HttpAuthHeader) -> JWTVerifier?) = { null }

        /**
         * JWT realm name that will be used during auth challenge
         */
        var realm: String = "Ktor Server"

        /**
         * Http auth header retrieval function. By default it does parse `Authorization` header content.
         */
        fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
            authHeader = block
        }

        /**
         * @param [defaultScheme] default scheme that will be used to challenge the client when no valid auth is provided
         * @param [additionalSchemes] additional schemes that will be accepted when validating the authentication
         */
        fun authSchemes(defaultScheme: String = "Bearer", vararg additionalSchemes: String) {
            schemes = JWTAuthSchemes(defaultScheme, *additionalSchemes)
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
        fun verifier(verifier: (HttpAuthHeader) -> JWTVerifier?) {
            this.verifier = verifier
        }

        /**
         * @param [jwkProvider] provides the JSON Web Key
         * @param [issuer] the issuer of the JSON Web Token
         */
        fun verifier(jwkProvider: JwkProvider, issuer: String) {
            this.verifier = { token -> getVerifier(jwkProvider, issuer, token, schemes) }
        }

        /**
         * @param [jwkProvider] provides the JSON Web Key
         */
        fun verifier(jwkProvider: JwkProvider) {
            this.verifier = { token -> getVerifier(jwkProvider, token, schemes) }
        }

        /**
         * Apply [validate] function to every call with [JWTCredential]
         * @return a principal (usually an instance of [JWTPrincipal]) or `null`
         */
        fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Principal?) {
            authenticationFunction = validate
        }

        internal fun build() = JWTAuthenticationProvider(this)
    }
}

internal class JWTAuthSchemes(val defaultScheme: String, vararg additionalSchemes: String) {
    val schemes = (arrayOf(defaultScheme) + additionalSchemes).toSet()
    val schemesLowerCase = schemes.map { it.toLowerCase() }.toSet()

    operator fun contains(scheme: String): Boolean = scheme.toLowerCase() in schemesLowerCase
}

/**
 * Installs JWT Authentication mechanism
 */
fun Authentication.Configuration.jwt(
    name: String? = null,
    configure: JWTAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = JWTAuthenticationProvider.Configuration(name).apply(configure).build()
    val realm = provider.realm
    val authenticate = provider.authenticationFunction
    val verifier = provider.verifier
    val schemes = provider.schemes
    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val token = provider.authHeader(call)
        if (token == null) {
            context.bearerChallenge(AuthenticationFailedCause.NoCredentials, realm, schemes)
            return@intercept
        }

        try {
            val principal = verifyAndValidate(call, verifier(token), token, schemes, authenticate)
            if (principal != null) {
                context.principal(principal)
            } else {
                context.bearerChallenge(AuthenticationFailedCause.InvalidCredentials, realm, schemes)
            }
        } catch (cause: Throwable) {
            val message = cause.message ?: cause.javaClass.simpleName
            JWTLogger.trace("JWT verification failed: {}", message)
            context.error(JWTAuthKey, AuthenticationFailedCause.Error(message))
        }
    }
    register(provider)
}

private fun AuthenticationContext.bearerChallenge(
    cause: AuthenticationFailedCause,
    realm: String,
    schemes: JWTAuthSchemes
) = challenge(JWTAuthKey, cause) {
    call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(realm, schemes)))
    it.complete()
}

private fun getVerifierNullableIssuer(
    jwkProvider: JwkProvider,
    issuer: String?,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes
): JWTVerifier? {
    val jwk = token.getBlob(schemes)?.let { blob ->
        try {
            jwkProvider.get(JWT.decode(blob).keyId)
        } catch (ex: JwkException) {
            JWTLogger.trace("Failed to get JWK: {}", ex.message)
            null
        } catch (ex: JWTDecodeException) {
            JWTLogger.trace("Illegal JWT: {}", ex.message)
            null
        }
    } ?: return null

    val algorithm = try {
        jwk.makeAlgorithm()
    } catch (cause: Throwable) {
        JWTLogger.trace("Failed to create algorithm {}: {}", jwk.algorithm, cause.message ?: cause.javaClass.simpleName)
        return null
    }

    return when (issuer) {
        null -> JWT.require(algorithm).build()
        else -> JWT.require(algorithm).withIssuer(issuer).build()
    }
}

private fun getVerifier(
    jwkProvider: JwkProvider,
    issuer: String,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes
): JWTVerifier? {
    return getVerifierNullableIssuer(jwkProvider, issuer, token, schemes)
}

private fun getVerifier(jwkProvider: JwkProvider, token: HttpAuthHeader, schemes: JWTAuthSchemes): JWTVerifier? {
    return getVerifierNullableIssuer(jwkProvider, null, token, schemes)
}

private suspend fun verifyAndValidate(
    call: ApplicationCall,
    jwtVerifier: JWTVerifier?,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    validate: suspend ApplicationCall.(JWTCredential) -> Principal?
): Principal? {
    val jwt = try {
        token.getBlob(schemes)?.let { jwtVerifier?.verify(it) }
    } catch (ex: JWTVerificationException) {
        JWTLogger.trace("Token verification failed: {}", ex.message)
        null
    } ?: return null

    val payload = jwt.parsePayload()
    val credentials = JWTCredential(payload)
    return validate(call, credentials)
}

private fun HttpAuthHeader.getBlob(schemes: JWTAuthSchemes) = when {
    this is HttpAuthHeader.Single && authScheme.toLowerCase() in schemes -> blob
    else -> null
}

private fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (ex: IllegalArgumentException) {
    JWTLogger.trace("Illegal HTTP auth header", ex)
    null
}

private fun HttpAuthHeader.Companion.bearerAuthChallenge(realm: String, schemes: JWTAuthSchemes): HttpAuthHeader =
    HttpAuthHeader.Parameterized(schemes.defaultScheme, mapOf(HttpAuthHeader.Parameters.Realm to realm))

internal fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
}

private fun DecodedJWT.parsePayload(): Payload {
    val payloadString = String(Base64.getUrlDecoder().decode(payload))
    return JWTParser().parsePayload(payloadString)
}

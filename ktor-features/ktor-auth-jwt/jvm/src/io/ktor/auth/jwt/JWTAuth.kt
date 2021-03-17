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
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.auth.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import org.slf4j.*
import java.security.interfaces.*
import java.util.*
import java.util.concurrent.*
import kotlin.reflect.*

private val JWTAuthKey: Any = "JWTAuth"

private val JWTLogger: Logger = LoggerFactory.getLogger("io.ktor.auth.jwt")

/**
 * Shortcut functions for standard registered [JWT Claims](https://tools.ietf.org/html/rfc7519#section-4.1)
 */
public abstract class JWTPayloadHolder(
    /**
     * The JWT payload
     */
    public val payload: Payload
) {

    /**
     * Get the value of the "iss" claim, or null if it's not available.
     *
     * The "iss" (issuer) claim identifies the principal that issued the
     * JWT.  The processing of this claim is generally application specific.
     * The "iss" value is a case-sensitive string containing a StringOrURI
     * value.  Use of this claim is OPTIONAL.
     */
    public val issuer: String? get() = payload.issuer

    /**
     * Get the value of the "sub" claim, or null if it's not available.
     *
     * The "sub" (subject) claim identifies the principal that is the
     * subject of the JWT.  The claims in a JWT are normally statements
     * about the subject.  The subject value MUST either be scoped to be
     * locally unique in the context of the issuer or be globally unique.
     * The processing of this claim is generally application specific.  The
     * "sub" value is a case-sensitive string containing a StringOrURI
     * value.  Use of this claim is OPTIONAL.
     */
    public val subject: String? get() = payload.subject

    /**
     * Get the value of the "aud" claim, or an empty list if it's not available.
     *
     * The "aud" (audience) claim identifies the recipients that the JWT is
     * intended for.  Each principal intended to process the JWT MUST
     * identify itself with a value in the audience claim.  If the principal
     * processing the claim does not identify itself with a value in the
     * "aud" claim when this claim is present, then the JWT MUST be
     * rejected.  In the general case, the "aud" value is an array of case-
     * sensitive strings, each containing a StringOrURI value.  In the
     * special case when the JWT has one audience, the "aud" value MAY be a
     * single case-sensitive string containing a StringOrURI value.  The
     * interpretation of audience values is generally application specific.
     * Use of this claim is OPTIONAL.
     */
    public val audience: List<String> get() = payload.audience ?: emptyList()

    /**
     * Get the value of the "exp" claim, or null if it's not available.
     *
     * The "exp" (expiration time) claim identifies the expiration time on
     * or after which the JWT MUST NOT be accepted for processing.  The
     * processing of the "exp" claim requires that the current date/time
     * MUST be before the expiration date/time listed in the "exp" claim.
     * Implementers MAY provide for some small leeway, usually no more than
     * a few minutes, to account for clock skew. Use of this claim is OPTIONAL.
     */
    public val expiresAt: Date? get() = payload.expiresAt

    /**
     * Get the value of the "nbf" claim, or null if it's not available.
     *
     * The "nbf" (not before) claim identifies the time before which the JWT
     * MUST NOT be accepted for processing.  The processing of the "nbf"
     * claim requires that the current date/time MUST be after or equal to
     * the not-before date/time listed in the "nbf" claim.  Implementers MAY
     * provide for some small leeway, usually no more than a few minutes, to
     * account for clock skew. Use of this claim is OPTIONAL.
     */
    public val notBefore: Date? get() = payload.notBefore

    /**
     * Get the value of the "iat" claim, or null if it's not available.
     *
     * The "iat" (issued at) claim identifies the time at which the JWT was
     * issued.  This claim can be used to determine the age of the JWT.
     * Use of this claim is OPTIONAL.
     */
    public val issuedAt: Date? get() = payload.issuedAt

    /**
     * Get the value of the "jti" claim, or null if it's not available.
     *
     * The "jti" (JWT ID) claim provides a unique identifier for the JWT.
     * The identifier value MUST be assigned in a manner that ensures that
     * there is a negligible probability that the same value will be
     * accidentally assigned to a different data object; if the application
     * uses multiple issuers, collisions MUST be prevented among values
     * produced by different issuers as well.  The "jti" claim can be used
     * to prevent the JWT from being replayed.  The "jti" value is a case-
     * sensitive string.  Use of this claim is OPTIONAL.
     */
    public val jwtId: String? get() = payload.id

    /**
     * Retrieve a non-RFC JWT string Claim by its name
     *
     * @param name The claim's key as it appears in the JSON object
     * @return the Claim's value or null if not available or not a string
     */
    public operator fun get(name: String): String? {
        return payload.getClaim(name).asString()
    }

    /**
     * Retrieve a non-RFC JWT Claim by its name and attempt to decode as the supplied type
     *
     * @param name The claim's key as it appears in the JSON object
     * @return the Claim's value or null if not available or unable to deserialise
     */
    public fun <T : Any> getClaim(name: String, clazz: KClass<T>): T? {
        return try {
            payload.getClaim(name).`as`(clazz.javaObjectType)
        } catch (ex: JWTDecodeException) {
            null
        }
    }

    /**
     * Retrieve a non-RFC JWT Claim by its name and attempt to decode as a list of the supplied type
     *
     * @param name The claim's key as it appears in the JSON object
     * @return the Claim's value or an empty list if not available or unable to deserialise
     */
    public fun <T : Any> getListClaim(name: String, clazz: KClass<T>): List<T> {
        return try {
            payload.getClaim(name).asList(clazz.javaObjectType)
        } catch (ex: JWTDecodeException) {
            emptyList()
        }
    }
}

/**
 * Represents a JWT credential consist of the specified [payload]
 * @param payload JWT
 * @see Payload
 */
public class JWTCredential(payload: Payload) : Credential, JWTPayloadHolder(payload)

/**
 * Represents a JWT principal consist of the specified [payload]
 * @param payload JWT
 * @see Payload
 */
public class JWTPrincipal(payload: Payload) : Principal, JWTPayloadHolder(payload)

/**
 * JWT verifier configuration function. It is applied on the verifier builder.
 */
public typealias JWTConfigureFunction = Verification.() -> Unit

/**
 * JWT authentication provider that will be registered with the specified [name]
 */
public class JWTAuthenticationProvider internal constructor(config: Configuration) : AuthenticationProvider(config) {

    internal val realm: String = config.realm
    internal val schemes: JWTAuthSchemes = config.schemes
    internal val authHeader: (ApplicationCall) -> HttpAuthHeader? = config.authHeader
    internal val verifier: ((HttpAuthHeader) -> JWTVerifier?) = config.verifier
    internal val authenticationFunction = config.authenticationFunction
    internal val challengeFunction: JWTAuthChallengeFunction = config.challenge

    /**
     * JWT auth provider configuration
     */
    public class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        internal var authenticationFunction: AuthenticationFunction<JWTCredential> = {
            throw NotImplementedError(
                "JWT auth validate function is not specified. Use jwt { validate { ... } } to fix."
            )
        }

        internal var schemes = JWTAuthSchemes("Bearer")

        internal var authHeader: (ApplicationCall) -> HttpAuthHeader? =
            { call -> call.request.parseAuthorizationHeaderOrNull() }

        internal var verifier: ((HttpAuthHeader) -> JWTVerifier?) = { null }

        internal var challenge: JWTAuthChallengeFunction = { scheme, realm ->
            call.respond(
                UnauthorizedResponse(
                    HttpAuthHeader.Parameterized(
                        scheme,
                        mapOf(HttpAuthHeader.Parameters.Realm to realm)
                    )
                )
            )
        }

        /**
         * JWT realm name that will be used during auth challenge
         */
        public var realm: String = "Ktor Server"

        /**
         * Http auth header retrieval function. By default it does parse `Authorization` header content.
         */
        public fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
            authHeader = block
        }

        /**
         * @param [defaultScheme] default scheme that will be used to challenge the client when no valid auth is provided
         * @param [additionalSchemes] additional schemes that will be accepted when validating the authentication
         */
        public fun authSchemes(defaultScheme: String = "Bearer", vararg additionalSchemes: String) {
            schemes = JWTAuthSchemes(defaultScheme, *additionalSchemes)
        }

        /**
         * @param [verifier] verifies token format and signature
         */
        public fun verifier(verifier: JWTVerifier) {
            this.verifier = { verifier }
        }

        /**
         * @param [verifier] verifies token format and signature
         */
        public fun verifier(verifier: (HttpAuthHeader) -> JWTVerifier?) {
            this.verifier = verifier
        }

        /**
         * @param [jwkProvider] provides the JSON Web Key
         * @param [issuer] the issuer of the JSON Web Token
         * * @param configure function will be applied during [JWTVerifier] construction
         */
        public fun verifier(jwkProvider: JwkProvider, issuer: String, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, issuer, token, schemes, configure) }
        }

        /**
         * @param [jwkProvider] provides the JSON Web Key
         * @param configure function will be applied during [JWTVerifier] construction
         */
        public fun verifier(jwkProvider: JwkProvider, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, token, schemes, configure) }
        }

        /**
         * Configure verifier using [JWTVerifier].
         *
         * @param issuer of the JSON Web Token
         * @param audience restriction
         * @param [algorithm] for validations of token signatures
         */
        public fun verifier(
            issuer: String,
            audience: String,
            algorithm: Algorithm,
            block: Verification.() -> Unit = {}
        ) {
            val verification: Verification = JWT
                .require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)

            verification.apply(block)
            verifier(verification.build())
        }

        /**
         * Configure verifier using [JwkProvider].
         *
         * @param [issuer] the issuer of JSON Web Token
         * @param [block] configuration of [JwkProvider]
         */
        public fun verifier(issuer: String, block: JWTConfigureFunction = {}) {
            val provider = JwkProviderBuilder(issuer).build()
            verifier = { token -> getVerifier(provider, token, schemes, block) }
        }

        /**
         * Apply [validate] function to every call with [JWTCredential]
         * @return a principal (usually an instance of [JWTPrincipal]) or `null`
         */
        public fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Principal?) {
            authenticationFunction = validate
        }

        /**
         * Specifies what to send back if jwt authentication fails.
         */
        public fun challenge(block: JWTAuthChallengeFunction) {
            challenge = block
        }

        internal fun build() = JWTAuthenticationProvider(this)
    }
}

/**
 * Installs JWT Authentication mechanism
 */
public fun Authentication.Configuration.jwt(
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
            context.bearerChallenge(AuthenticationFailedCause.NoCredentials, realm, schemes, provider.challengeFunction)
            return@intercept
        }

        try {
            val principal = verifyAndValidate(call, verifier(token), token, schemes, authenticate)
            if (principal != null) {
                context.principal(principal)
                return@intercept
            }

            context.bearerChallenge(
                AuthenticationFailedCause.InvalidCredentials,
                realm,
                schemes,
                provider.challengeFunction
            )
        } catch (cause: Throwable) {
            val message = cause.message ?: cause.javaClass.simpleName
            JWTLogger.trace("JWT verification failed: {}", message)
            context.error(JWTAuthKey, AuthenticationFailedCause.Error(message))
        }
    }
    register(provider)
}

/**
 * Specifies what to send back if session authentication fails.
 */
public typealias JWTAuthChallengeFunction =
    suspend PipelineContext<*, ApplicationCall>.(defaultScheme: String, realm: String) -> Unit

private fun AuthenticationContext.bearerChallenge(
    cause: AuthenticationFailedCause,
    realm: String,
    schemes: JWTAuthSchemes,
    challengeFunction: JWTAuthChallengeFunction
) {
    challenge(JWTAuthKey, cause) {
        challengeFunction(this, schemes.defaultScheme, realm)
        if (!it.completed && call.response.status() != null) {
            it.complete()
        }
    }
}

private fun getVerifier(
    jwkProvider: JwkProvider,
    issuer: String?,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    jwtConfigure: Verification.() -> Unit
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
        null -> JWT.require(algorithm)
        else -> JWT.require(algorithm).withIssuer(issuer)
    }.apply(jwtConfigure).build()
}

private fun getVerifier(
    jwkProvider: JwkProvider,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    configure: JWTConfigureFunction
): JWTVerifier? {
    return getVerifier(jwkProvider, null, token, schemes, configure)
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
    this is HttpAuthHeader.Single && authScheme in schemes -> blob
    else -> null
}

private fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (ex: IllegalArgumentException) {
    JWTLogger.trace("Illegal HTTP auth header", ex)
    null
}

private fun DecodedJWT.parsePayload(): Payload {
    val payloadString = String(Base64.getUrlDecoder().decode(payload))
    return JWTParser().parsePayload(payloadString)
}

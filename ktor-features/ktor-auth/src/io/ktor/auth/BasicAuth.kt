package io.ktor.auth

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import java.nio.charset.*
import java.util.*


/**
 * Represents a Basic authentication provider
 * @param name is the name of the provider, or `null` for a default provider
 */
class BasicAuthenticationProvider(name: String?) : AuthenticationProvider(name) {
    internal var authenticationFunction: suspend ApplicationCall.(UserPasswordCredential) -> Principal? = { null }

    /**
     * Specifies realm to be passed in `WWW-Authenticate` header
     */
    var realm: String = "Ktor Server"

    /**
     * Specifies the charset to be used.
     */
    var charset: Charset = Charsets.UTF_8

    /**
     * Sets a validation function that will check given [UserPasswordCredential] instance and return [Principal],
     * or null if credential does not correspond to an authenticated principal
     */
    fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Principal?) {
        authenticationFunction = body
    }
}

/**
 * Installs Basic Authentication mechanism
 */
fun Authentication.Configuration.basic(name: String? = null, configure: BasicAuthenticationProvider.() -> Unit) {
    val provider = BasicAuthenticationProvider(name).apply(configure)
    val realm = provider.realm
    val charset = provider.charset
    val authenticate = provider.authenticationFunction

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val credentials = call.request.basicAuthenticationCredentials(charset)
        val principal = credentials?.let { authenticate(call, it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(basicAuthenticationChallengeKey, cause) {
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge(realm, charset)))
                it.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    register(provider)
}

/**
 * Retrieves Basic authentication credentials for this [ApplicationRequest]
 */
fun ApplicationRequest.basicAuthenticationCredentials(charset: Charset = Charsets.UTF_8): UserPasswordCredential? {
    val parsed = parseAuthorizationHeader()
    when (parsed) {
        is HttpAuthHeader.Single -> {
            // Verify the auth scheme is HTTP Basic. According to RFC 2617, the authorization scheme should not be case
            // sensitive; thus BASIC, or Basic, or basic are all valid.
            if (!parsed.authScheme.equals("Basic", ignoreCase = true)) {
                return null
            }

            val userPass = try {
                Base64.getDecoder().decode(parsed.blob).toString(charset)
            } catch (e : IllegalArgumentException) {
                return null
            }

            if (":" !in userPass) {
                return null
            }

            return UserPasswordCredential(userPass.substringBefore(":"), userPass.substringAfter(":"))
        }
        else -> return null
    }
}

private val basicAuthenticationChallengeKey: Any = "BasicAuth"


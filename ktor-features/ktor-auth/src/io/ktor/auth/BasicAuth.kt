package io.ktor.auth

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import java.util.*

/**
 * Installs Basic Authentication mechanism into [AuthenticationPipeline]
 */
fun AuthenticationPipeline.basicAuthentication(realm: String, validate: suspend (UserPasswordCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val credentials = call.request.basicAuthenticationCredentials()
        val principal = credentials?.let { validate(it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(basicAuthenticationChallengeKey, cause) {
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge(realm)))
                it.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }
}

/**
 * Retrieves Basic authentication credentials for this [ApplicationRequest]
 */
fun ApplicationRequest.basicAuthenticationCredentials(): UserPasswordCredential? {
    val parsed = parseAuthorizationHeader()
    when (parsed) {
        is HttpAuthHeader.Single -> {
            // here we can only use ISO 8859-1 character encoding because there is no character encoding specified as per RFC
            //     see http://greenbytes.de/tech/webdav/draft-reschke-basicauth-enc-latest.html
            //      http://tools.ietf.org/html/draft-ietf-httpauth-digest-15
            //      https://bugzilla.mozilla.org/show_bug.cgi?id=41489
            //      https://code.google.com/p/chromium/issues/detail?id=25790

            val userPass = Base64.getDecoder().decode(parsed.blob).toString(Charsets.ISO_8859_1)

            if (":" !in userPass) {
                return null
            }

            return UserPasswordCredential(userPass.substringBefore(":"), userPass.substringAfter(":"))
        }
        else -> return null
    }
}

private val basicAuthenticationChallengeKey: Any = "BasicAuth"


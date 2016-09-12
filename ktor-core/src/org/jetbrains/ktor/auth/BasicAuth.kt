package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

fun ApplicationCall.basicAuthenticationCredentials(): UserPasswordCredential? = request.basicAuthenticationCredentials()
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

val BasicAuthKey: Any = "BasicAuth"
fun Authentication.Pipeline.basicAuthentication(realm: String, validate: (UserPasswordCredential) -> Principal?) {
    intercept(Authentication.Pipeline.RequestAuthentication) { context ->
        val credentials = context.call.request.basicAuthenticationCredentials()
        val principal = credentials?.let(validate)

        val cause = when {
            credentials == null -> NotAuthenticatedCause.NoCredentials
            principal == null -> NotAuthenticatedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(BasicAuthKey, cause) {
                it.success()
                context.call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge(realm)))
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }
}

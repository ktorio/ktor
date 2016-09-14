package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.security.*

// See for details http://www.faqs.org/rfcs/rfc2617.html

data class DigestCredential(val realm: String,
                            val userName: String,
                            val digestUri: String,
                            val nonce: String,
                            val opaque: String?,
                            val nonceCount: String?,
                            val algorithm: String?,
                            val response: String,
                            val cnonce: String?,
                            val qop: String?) : Credential

fun ApplicationCall.extractDigest(): DigestCredential? {
    return request.parseAuthorizationHeader()?.let { authHeader ->
        if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
            return authHeader.toDigestCredential()
        } else {
            null
        }
    }
}

val DigestAuthKey: Any = "DigestAuth"

fun Authentication.Pipeline.digestAuthentication(
        realm: String = "ktor",
        digestAlgorithm: String = "MD5",
        digesterProvider: (String) -> MessageDigest = { MessageDigest.getInstance(it) },
        userNameRealmPasswordDigestProvider: (String, String) -> ByteArray) {

    val digester = digesterProvider(digestAlgorithm)
    intercept(Authentication.Pipeline.RequestAuthentication) { context ->
        val authorizationHeader = context.call.request.parseAuthorizationHeader()
        val credentials = authorizationHeader?.let { authHeader ->
            if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
                authHeader.toDigestCredential()
            } else
                null
        }

        val principal = credentials?.let {
            if ((it.algorithm ?: "MD5") == digestAlgorithm && it.verify(context.call.request.local.method, digester, userNameRealmPasswordDigestProvider))
                UserIdPrincipal(it.userName)
            else
                null
        }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause = when {
                credentials == null -> NotAuthenticatedCause.NoCredentials
                else -> NotAuthenticatedCause.InvalidCredentials
            }

            context.challenge(DigestAuthKey, cause) {
                it.success()
                context.call.respond(UnauthorizedResponse(HttpAuthHeader.digestAuthChallenge(realm)))
            }
        }
    }
}


fun HttpAuthHeader.Parameterized.toDigestCredential() = DigestCredential(
        parameter("realm")!!,
        parameter("username")!!,
        parameter("uri")!!,
        parameter("nonce")!!,
        parameter("opaque"),
        parameter("nc"),
        parameter("algorithm"),
        parameter("response")!!,
        parameter("cnonce"),
        parameter("qop")
)

fun DigestCredential.verify(method: HttpMethod, digester: MessageDigest, userNameRealmPasswordDigest: (String, String) -> ByteArray): Boolean {
    val validDigest = expectedDigest(method, digester, userNameRealmPasswordDigest(userName, realm))

    return response == validDigest
}

fun DigestCredential.expectedDigest(method: HttpMethod, digester: MessageDigest, userNameRealmPasswordDigest: ByteArray): String {
    fun digest(data: String): String {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return hex(digester.digest())
    }

    val start = hex(userNameRealmPasswordDigest)
    val end = digest("${method.value.toUpperCase()}:$digestUri")

    val a = listOf(start, nonce, nonceCount, cnonce, qop, end).map { it ?: "" }.joinToString(":")
    return digest(a)
}
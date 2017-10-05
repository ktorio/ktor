package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
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

fun AuthenticationPipeline.digestAuthentication(
        realm: String = "ktor",
        digestAlgorithm: String = "MD5",
        digesterProvider: (String) -> MessageDigest = { MessageDigest.getInstance(it) },
        userNameRealmPasswordDigestProvider: (String, String) -> ByteArray) {

    val digester = digesterProvider(digestAlgorithm)
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val authorizationHeader = call.request.parseAuthorizationHeader()
        val credentials = authorizationHeader?.let { authHeader ->
            if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
                authHeader.toDigestCredential()
            } else
                null
        }

        val principal = credentials?.let {
            if ((it.algorithm ?: "MD5") == digestAlgorithm && it.verify(call.request.local.method, digester, userNameRealmPasswordDigestProvider))
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
                call.respond(UnauthorizedResponse(HttpAuthHeader.digestAuthChallenge(realm)))
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

    val incoming: ByteArray = try {
        hex(response)
    } catch(e: NumberFormatException) {
        return false
    }
    return MessageDigest.isEqual(incoming, validDigest)
}

fun DigestCredential.expectedDigest(method: HttpMethod, digester: MessageDigest, userNameRealmPasswordDigest: ByteArray): ByteArray {
    fun digest(data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }

    val start = hex(userNameRealmPasswordDigest)
    val end = hex(digest("${method.value.toUpperCase()}:$digestUri"))

    val a = listOf<String?>(start, nonce, nonceCount, cnonce, qop, end).map { it ?: "" }.joinToString(":")
    return digest(a)
}
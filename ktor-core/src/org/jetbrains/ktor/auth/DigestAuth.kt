package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
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

fun <C: ApplicationCall> AuthBuilder<C>.extractDigest() {
    intercept { next ->
        request.parseAuthorizationHeader()?.let { authHeader ->
            if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
                authContext.addCredential(authHeader.toDigestCredential())
            }
        }

        next()
    }
}

fun <C: ApplicationCall> AuthBuilder<C>.digestAuth(
        digestAlgorithm: String = "MD5",
        digesterProvider: (String) -> MessageDigest = { MessageDigest.getInstance(it) },
        userNameRealmPasswordDigestProvider: (String, String) -> ByteArray) {

    val digester = digesterProvider(digestAlgorithm)

    extractDigest()

    verifyBatchTypedWith { digests: List<DigestCredential> ->
        digests.filter { (it.algorithm ?: "MD5") == digestAlgorithm }
            .filter { it.verify(request.httpMethod, digester, userNameRealmPasswordDigestProvider) }
            .map { UserIdPrincipal(it.userName) }
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

fun DigestCredential.verify(method: HttpMethod, digester: MessageDigest, userNameRealmPasswordDigest: (String, String) -> ByteArray): Boolean =
    verify(method, digester, userNameRealmPasswordDigest(userName, realm))

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

fun DigestCredential.verify(method: HttpMethod, digester: MessageDigest, userNameRealmPasswordDigest: ByteArray): Boolean {
    val validDigest = expectedDigest(method, digester, userNameRealmPasswordDigest)

    return response == validDigest
}
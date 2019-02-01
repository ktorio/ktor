package io.ktor.util

@InternalAPI
actual fun generateNonce(): String = error("[generateNonce] is not supported on iOS")

@InternalAPI
actual fun Digest(name: String): Digest = error("[Digest] is not supported on iOS")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}

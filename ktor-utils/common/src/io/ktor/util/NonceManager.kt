package io.ktor.util


/**
 * Represents a nonce manager. It's responsibility is to produce nonce values
 * and verify nonce values from untrusted sources that they are provided by this manager.
 * This is usually required in web environment to mitigate CSRF attacks.
 * Depending on it's underlying implementation it could be stateful or stateless.
 * Note that there is usually some timeout for nonce values to reduce memory usage and to avoid replay attacks.
 * Nonce length is unspecified.
 */
@KtorExperimentalAPI
interface NonceManager {
    /**
     * Generate new nonce instance
     */
    suspend fun newNonce(): String

    /**
     * Verify [nonce] value
     * @return `true` if [nonce] is valid
     */
    suspend fun verifyNonce(nonce: String): Boolean
}


/**
 * This implementation does only generate nonce values but doesn't validate them. This is recommended for testing only.
 */
@KtorExperimentalAPI
object GenerateOnlyNonceManager : NonceManager {
    override suspend fun newNonce(): String {
        return generateNonce()
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        return true
    }
}

/**
 * Stub implementation that always fails.
 * Will be removed so no public signatures should rely on it
 */
@Deprecated("This should be removed with OAuth2StateProvider")
@InternalAPI
object AlwaysFailNonceManager : NonceManager {
    override suspend fun newNonce(): String {
        throw UnsupportedOperationException("This manager should never be used")
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        throw UnsupportedOperationException("This manager should never be used")
    }
}

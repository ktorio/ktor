package io.ktor.auth

import io.ktor.config.*
import io.ktor.util.*
import java.security.MessageDigest

/**
 * Represents a simple user's principal identified by [name]
 * @property name of user
 */
data class UserIdPrincipal(val name: String) : Principal

/**
 * Represents a simple user [name] and [password] credential pair
 * @property name
 * @property password
 */
data class UserPasswordCredential(val name: String, val password: String) : Credential

/**
 * Simple in-memory table that keeps user names and password hashes
 * @property digester a hash function to compute password digest
 * @property table of user names and hashed passwords
 */
@KtorExperimentalAPI
class UserHashedTableAuth(val digester: (String) -> ByteArray, val table: Map<String, ByteArray>) {

    // shortcut for tests
    constructor(table: Map<String, ByteArray>) : this(getDigestFunction("SHA-256", "ktor"), table)

    constructor(config: ApplicationConfig) : this(
        getDigestFunction(
            config.property("hashAlgorithm").getString(),
            config.property("salt").getString()
        ), config.parseUsers()
    )

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    /**
     * Authenticate user by [credential] and return an instance of [UserIdPrincipal]
     * if the [credential] pair is valid
     */
    fun authenticate(credential: UserPasswordCredential): UserIdPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && MessageDigest.isEqual(digester(credential.password), userPasswordHash)) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}

private fun ApplicationConfig.parseUsers(name: String = "users") =
    configList(name)
        .map { it.property("name").getString() to decodeBase64(it.property("hash").getString()) }
        .toMap()

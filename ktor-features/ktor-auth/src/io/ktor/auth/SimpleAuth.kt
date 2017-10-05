package io.ktor.auth

import io.ktor.config.*
import io.ktor.util.*
import java.security.MessageDigest

data class UserIdPrincipal(val name: String) : Principal
data class UserPasswordCredential(val name: String, val password: String) : Credential

class UserHashedTableAuth(val digester: (String) -> ByteArray, val table: Map<String, ByteArray>) {

    // shortcut for tests
    constructor(table: Map<String, ByteArray>) : this(getDigestFunction("SHA-256", "ktor"), table)

    constructor(config: ApplicationConfig) : this(getDigestFunction(
            config.property("hashAlgorithm").getString(),
            config.property("salt").getString()), config.parseUsers())

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

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

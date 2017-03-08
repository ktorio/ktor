package org.jetbrains.ktor.auth

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.util.*
import java.util.*

data class UserIdPrincipal(val name: String) : Principal
data class UserPasswordCredential(val name: String, val password: String) : Credential

class UserHashedTableAuth(val digester: (String) -> ByteArray = getDigestFunction("SHA-256", "ktor"), val table: Map<String, ByteArray>) {

    // TODO: Use ApplicationConfig instead of HOCON
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
        if (userPasswordHash != null && Arrays.equals(digester(credential.password), userPasswordHash)) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}

private fun ApplicationConfig.parseUsers(name: String = "users") =
        configList(name)
                .map { it.property("name").getString() to decodeBase64(it.property("hash").getString()) }
                .toMap()

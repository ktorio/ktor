package org.jetbrains.ktor.auth

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import java.util.*

data class UserIdPrincipal(val name: String) : Principal
data class UserPasswordCredential(val name: String, val password: String) : Credential

public class UserHashedTableAuth(val digester: (String) -> ByteArray = getDigestFunction("SHA-256", "ktor"), val table: Map<String, ByteArray>) {

    constructor(config: Config) : this(getDigestFunction(config.getString("hashAlgorithm"), config.getString("salt")), config.parseUsers())

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

fun <C: ApplicationCall> AuthBuilder<C>.verifyBatchTypedWith(authTableAuth: UserHashedTableAuth) {
    verifyBatchTypedWith { c: List<UserPasswordCredential> -> c.map { authTableAuth.authenticate(it) }.filterNotNull() }
}

private fun Config.parseUsers(name: String = "users") =
        getConfigList(name)
                .map { it.getString("name")!! to decodeBase64(it.getString("hash")) }
                .toMap()

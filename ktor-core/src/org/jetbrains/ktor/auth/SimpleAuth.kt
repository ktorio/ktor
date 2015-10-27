package org.jetbrains.ktor.auth.simple

import com.typesafe.config.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.crypto.*
import java.util.*

data class SimpleUserPrincipal(val name: String, val groups: List<String> = emptyList())
data class SimpleUserPassword(val name: String, val password: String)

public class SimpleUserHashedTableAuth(val digester: (String) -> ByteArray = getDigestFunction("SHA-256", "ktor"), val table: Map<String, ByteArray>) {

    constructor(config: Config) : this(getDigestFunction(config.getString("hashAlgorithm"), config.getString("salt")), config.parseUsers())

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    fun authenticate(credential: SimpleUserPassword): SimpleUserPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && Arrays.equals(digester(credential.password), userPasswordHash)) {
            return SimpleUserPrincipal(credential.name)
        }

        return null
    }
}

public class SimpleUserEncryptedTableAuth(val decryptor: PasswordDecryptor, val table: Map<String, String>) {
    fun authenticate(credential: SimpleUserPassword): SimpleUserPrincipal? {
        if (decrypt(credential.name) == credential.password) {
            return SimpleUserPrincipal(credential.name)
        }

        return null
    }

    private fun decrypt(name: String) = table[name]?.let { decryptor.decrypt(it) }
}

private fun Config.parseUsers(name: String = "users") =
        getConfigList(name)
                .map { it.getString("name")!! to base64(it.getString("hash")) }
                .toMap()

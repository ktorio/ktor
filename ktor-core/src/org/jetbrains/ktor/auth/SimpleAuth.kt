package org.jetbrains.ktor.auth

import com.typesafe.config.*
import org.jetbrains.ktor.auth.crypto.*
import java.util.*

data class SimpleUserPrincipal(val name: String)
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

private fun Config.parseUsers(name: String = "users") =
        getConfigList(name)
                .map { it.getString("name")!! to decodeBase64(it.getString("hash")) }
                .toMap()

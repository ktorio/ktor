package org.jetbrains.ktor.http.auth.simple

import com.typesafe.config.*
import org.jetbrains.ktor.http.auth.*
import org.jetbrains.ktor.http.auth.Principal
import java.security.*
import java.util.*

data class SimpleUserPrincipal(override val name: String) : Principal
data class SimpleUserPassword(val name: String, val password: String) : Credential

class SimpleUserTableAuth(val digester: (String) -> ByteArray = getShaDigestFunction("SHA256", "ktor"), val table: Map<String, ByteArray>) : AuthenticationProvider<SimpleUserPassword, SimpleUserPrincipal> {

    constructor(config: Config) : this(getShaDigestFunction(config.getString("hashAlgorithm"), config.getString("salt")), config.parseUsers())

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    override fun authenticate(credential: SimpleUserPassword): SimpleUserPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && Arrays.equals(digester(credential.password), userPasswordHash)) {
            return SimpleUserPrincipal(credential.name)
        }

        return null
    }
}

public fun getShaDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = { e -> shaDigest(e, algorithm, salt) }

public fun shaDigest(text: String, algorithm: String, salt: String): ByteArray = with(MessageDigest.getInstance(algorithm)) {
    update(salt.toByteArray())
    digest(text.toByteArray())
}

private fun Config.parseUsers(name: String = "users") =
        getConfigList(name)
                .map { it.getString("name")!! to it.getString("hash").decodeHex() }
                .toMap()

private fun String.decodeHex(): ByteArray {
    require(length() mod 2 == 0) { "HEX string is not valid: $this" }
    return (0..length() step 2).map { java.lang.Byte.valueOf(this[it].toString() + this[it + 1], 16) }.toByteArray()
}

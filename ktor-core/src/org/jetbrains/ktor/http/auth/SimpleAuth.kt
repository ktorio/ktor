package org.jetbrains.ktor.http.auth.simple

import com.typesafe.config.*
import org.jetbrains.ktor.http.auth.*
import java.security.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

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

public fun getDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = { e -> getDigest(e, algorithm, salt) }

public fun getDigest(text: String, algorithm: String, salt: String): ByteArray = with(MessageDigest.getInstance(algorithm)) {
    update(salt.toByteArray())
    digest(text.toByteArray())
}

public class SimpleJavaCryptoPasswordDecryptor(val algorithmWithTransformation: String, val key: String, val salt: String) : PasswordDecryptor {
    private val iv: IvParameterSpec by lazy { IvParameterSpec(salt.toByteArray()) }
    private val keySpec: SecretKeySpec by lazy { SecretKeySpec(key.toByteArray(), algorithmWithTransformation.substringBefore('/')) }

    override fun decrypt(encrypted: String): String {
        val encryptedBytes = Base64.getDecoder().decode(encrypted)

        val cipher = Cipher.getInstance(algorithmWithTransformation)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)

        return cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
    }
}

private fun Config.parseUsers(name: String = "users") =
        getConfigList(name)
                .map { it.getString("name")!! to it.getString("hash").decodeBase64() }
                .toMap()

private fun String.decodeBase64() = Base64.getDecoder().decode(this)
private fun ByteArray.toBase64() = Base64.getEncoder().encodeToString(this)

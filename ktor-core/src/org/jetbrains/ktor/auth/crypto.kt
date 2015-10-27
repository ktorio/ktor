package org.jetbrains.ktor.auth.crypto

import org.jetbrains.ktor.auth.*
import java.security.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

fun getDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = { e -> getDigest(e, algorithm, salt) }

fun getDigest(text: String, algorithm: String, salt: String): ByteArray = with(MessageDigest.getInstance(algorithm)) {
    update(salt.toByteArray())
    digest(text.toByteArray())
}

class SimpleJavaCryptoPasswordDecryptor(val algorithmWithTransformation: String, private val key: ByteArray, val salt: ByteArray) : PasswordDecryptor {
    private val iv: IvParameterSpec by lazy { IvParameterSpec(salt) }
    private val keySpec: SecretKeySpec by lazy { SecretKeySpec(key, algorithmWithTransformation.substringBefore('/')) }

    override fun decrypt(encrypted: String): String {
        val encryptedBytes = Base64.getDecoder().decode(encrypted)

        val cipher = Cipher.getInstance(algorithmWithTransformation)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)

        return cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
    }
}

fun base64(s: String) = Base64.getDecoder().decode(s)
fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

// useful to work with openssl command line tool
fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in 0..result.size() - 1) {
        val srcIdx = idx * 2
        result[idx] = ((Integer.parseInt(s[srcIdx].toString(), 16)) shl 4 or Integer.parseInt(s[srcIdx + 1].toString(), 16)).toByte()
    }

    return result
}
fun hex(bytes: ByteArray) = bytes.map { Integer.toHexString(it.toInt() and 0xff).padStart(2, '0') }.joinToString("")

fun raw(s: String) = s.toByteArray(Charsets.UTF_8)

package org.jetbrains.ktor.samples.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.basic.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.auth.simple.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import javax.crypto.*
import javax.crypto.spec.*

@location("/manual") class Manual()
@location("/userTable") class SimpleUserTable()
@location("/encryptedTable") class EncryptedUserTable()

class BasicAuthApplication(config: ApplicationConfig) : Application(config) {
    val hashedUserTable = SimpleUserHashedTableAuth(table = mapOf(
            "test" to base64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))
    val encryptedUserTable = SimpleUserEncryptedTableAuth(
            // NOTICE: you should never hardcode it like that. Keep key in safe
            // see GenerateKeyAndEncryptExample for examples how to generate key and encrypt passwords
            decryptor = SimpleJavaCryptoPasswordDecryptor("AES/CBC/PKCS5Padding", key = base64("gCEkl6oKcT2RZMlrl3XyIg=="), salt = ByteArray(16)),
            table = mapOf(
                    "user1" to "YEi1rusdp566S5M26cT6yQ==" // encrypted "test me not"
            )
    )

    init {
        locations {
            get<Manual>() {
                basicAuthValidate { userPass ->
                    userPass.name == userPass.password
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
            get<SimpleUserTable>() {
                basicAuthValidate {
                    hashedUserTable.authenticate(it) != null
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
            get<EncryptedUserTable>() {
                basicAuthValidate {
                    encryptedUserTable.authenticate(it) != null
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
        }
    }
}

class GenerateKeyAndEncryptExample {
    object GenerateKey {
        fun main(args: Array<String>) {
            val kgen = KeyGenerator.getInstance("AES")
            kgen.init(128)
            val key = kgen.generateKey()

            println("Key: ${base64(key.encoded)}")
        }
    }

    // you actually can use openssl tool like this:
    // > echo -n 'test me not' | openssl enc -a -aes-128-cbc -K 80212497aa0a713d9164c96b9775f222 -iv 00000000000000000000000000000000
    // notice that -K and -iv requires argument in HEX format, not base64
    object Encrypt {
        fun main(args: Array<String>) {
            val key = SecretKeySpec(base64("gCEkl6oKcT2RZMlrl3XyIg=="), "AES")
            val iv = IvParameterSpec(ByteArray(16))

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            val encrypted = cipher.doFinal("test me not".toByteArray())

            println("Encrypted: ${base64(encrypted)}")
        }
    }

    object ConvertBase64ToHex {
        fun main(args: Array<String>) {
            println(hex(base64("gCEkl6oKcT2RZMlrl3XyIg==")))
        }
    }
}

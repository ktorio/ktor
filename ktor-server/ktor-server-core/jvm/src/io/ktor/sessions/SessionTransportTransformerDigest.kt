/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.sessions

import io.ktor.util.*
import java.security.*

private const val delimiter = '/'

/**
 * Session transformer that appends an [algorithm] hash of the input.
 * Where the input is either a session contents or a previous transformation.
 * It prepends a [salt] when computing the hash.
 *
 * @property salt that is used for message digest algorithm
 * @property algorithm is a message digest algorithm
 */
@Deprecated(
    "This authentication kind is potentially vulnerable with several hash functions." +
        " Use SessionTransportTransformerMessageAuthentication instead or ensure you are using secure enough hash.",
    level = DeprecationLevel.ERROR
)
public class SessionTransportTransformerDigest(
    public val salt: String = "ktor",
    public val algorithm: String = "SHA-384"
) : SessionTransportTransformer {

    override fun transformRead(transportValue: String): String? {
        val providedSignature = transportValue.substringAfterLast(delimiter, "")
        val value = transportValue.substringBeforeLast(delimiter)

        val providedBytes = try {
            hex(providedSignature)
        } catch (e: NumberFormatException) {
            return null
        }
        if (MessageDigest.isEqual(providedBytes, digest(value))) {
            return value
        }
        return null
    }

    override fun transformWrite(transportValue: String): String =
        transportValue + delimiter + hex(digest(transportValue))

    private fun digest(value: String): ByteArray {
        val md = MessageDigest.getInstance(algorithm)
        md.update(salt.toByteArray())
        md.update(value.toByteArray())
        return md.digest()
    }
}

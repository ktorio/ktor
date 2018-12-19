package io.ktor.util

import java.util.*

/**
 * Decode bytes from a BASE64 string [s]
 */
@InternalAPI
fun decodeBase64(s: String): ByteArray = Base64.getDecoder().decode(s)

/**
 * Encode [bytes] as a BASE64 string
 */
@InternalAPI
fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

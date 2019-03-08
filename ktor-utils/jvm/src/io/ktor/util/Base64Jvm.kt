package io.ktor.util

import kotlinx.io.charsets.Charset


/**
 * Decode bytes from a BASE64 string [s]
 */
@InternalAPI
fun decodeBase64(s: String): ByteArray = s.decodeBase64().toByteArray(Charset.forName("ISO-8859-1"))

/**
 * Encode [bytes] as a BASE64 string
 */
@InternalAPI
fun encodeBase64(bytes: ByteArray): String = bytes.encodeBase64()

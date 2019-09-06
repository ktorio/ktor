/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

/**
 * Decode bytes from a BASE64 string [encodedString]
 */
@InternalAPI
@Deprecated(
    "USe decodeBase64Bytes instead",
    ReplaceWith("encodedString.decodeBase64Bytes()"),
    level = DeprecationLevel.ERROR
)
fun decodeBase64(encodedString: String): ByteArray = encodedString.decodeBase64Bytes()

/**
 * Encode [bytes] as a BASE64 string
 */
@InternalAPI
@Deprecated(
    "Use encodeBase64 extension instead", ReplaceWith("bytes.encodeBase64()"),
    level = DeprecationLevel.ERROR
)
fun encodeBase64(bytes: ByteArray): String = bytes.encodeBase64()

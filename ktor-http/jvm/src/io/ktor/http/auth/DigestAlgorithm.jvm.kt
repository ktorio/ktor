/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import java.security.MessageDigest

/**
 * Creates a [MessageDigest] instance for this algorithm.
 *
 * @return A new MessageDigest configured for this algorithm's hash function
 * @throws [java.security.NoSuchAlgorithmException] If the algorithm is not supported by the JVM
 */
public fun DigestAlgorithm.toDigester(): MessageDigest =
    MessageDigest.getInstance(hashName)

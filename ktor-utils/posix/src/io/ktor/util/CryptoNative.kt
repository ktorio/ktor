/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util


/**
 * Generates a nonce string. Could block if the system's entropy source is empty
 */
@InternalAPI
actual fun generateNonce(): String = error("[generateNonce] is not supported on iOS")

/**
 * Create [Digest] from specified hash [name].
 */
@InternalAPI
actual fun Digest(name: String): Digest = error("[Digest] is not supported on iOS")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray = error("sha1 currently is not supported in ktor-native")

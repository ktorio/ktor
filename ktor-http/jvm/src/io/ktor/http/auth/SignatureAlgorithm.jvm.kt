/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import java.security.Signature

/**
 * Creates a [Signature] instance for this algorithm.
 *
 * @return A new Signature configured for this algorithm's JCA signature function
 * @throws [java.security.NoSuchAlgorithmException] If the algorithm is not supported by the JVM
 */
public fun SignatureAlgorithm.toJcaSignature(): Signature =
    Signature.getInstance(jcaAlgorithm)

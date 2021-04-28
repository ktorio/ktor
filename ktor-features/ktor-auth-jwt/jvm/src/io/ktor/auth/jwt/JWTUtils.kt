/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.algorithms.*
import java.security.interfaces.*

internal fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
}

/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * Creates a [Signature] instance for this algorithm.
 *
 * For RSASSA-PSS algorithms, the signature is configured with MGF1 using the same digest as [SignatureAlgorithm.digestAlgorithm]
 * and a salt length equal to the digest length.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.toJcaSignature)
 *
 * @return A new Signature configured for this algorithm's JCA signature function
 * @throws [java.security.NoSuchAlgorithmException] If the algorithm is not supported by the JVM
 */
public fun SignatureAlgorithm.toJcaSignature(): Signature {
    val signature = Signature.getInstance(jcaAlgorithm)
    if (jcaAlgorithm == RSASSA_PSS) {
        signature.setParameter(pssParameterSpec())
    }
    return signature
}

private const val RSASSA_PSS = "RSASSA-PSS"

// PSSParameterSpec.TRAILER_FIELD_BC is only available since JDK 11
private const val PSS_TRAILER_FIELD_BC = 1

private fun SignatureAlgorithm.pssParameterSpec(): PSSParameterSpec {
    val hashName = digestAlgorithm.hashName
    val saltLength = when (hashName) {
        "SHA-256" -> 32
        "SHA-384" -> 48
        "SHA-512" -> 64
        else -> throw IllegalArgumentException("Unsupported RSASSA-PSS digest algorithm: $hashName")
    }
    val mgfSpec = MGF1ParameterSpec(hashName)
    val mgfName = "MGF1"
    return PSSParameterSpec(hashName, mgfName, mgfSpec, saltLength, PSS_TRAILER_FIELD_BC)
}

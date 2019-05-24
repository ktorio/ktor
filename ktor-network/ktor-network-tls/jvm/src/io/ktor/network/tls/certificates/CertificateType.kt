/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

/**
 * Type of client certificate.
 * see also https://tools.ietf.org/html/rfc5246#section-7.4.4
 *
 * @property code numeric algorithm codes
 */
@Suppress("KDocMissingDocumentation")
internal object CertificateType {
    val RSA: Byte = 1
    val DSS: Byte = 2
    val RSA_FIXED_DH: Byte = 3
    val DSS_FIXED_DH: Byte = 4
    val RSA_EPHEMERAL_DH_RESERVED: Byte = 5
    val DSS_EPHEMERAL_DH_RESERVED: Byte = 6
    val FORTEZZA_DMS_RESERVED: Byte = 20
}

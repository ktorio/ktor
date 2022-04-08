/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.cinterop.*
import io.ktor.network.tls.internal.openssl.*
import platform.posix.*

internal inline fun PKCS12Certificate.parse(
    block: (
        privateKey: CPointer<EVP_PKEY>,
        x509Certificate: CPointer<X509>,
    ) -> Unit
) {
    val pkcs12 = useFile(path, "rb") { file ->
        requireNotNull(d2i_PKCS12_fp(file, null)) { "Failed to read PKCS12 file" }
    }
    try {
        val password = password?.invoke()

        val pkey = nativeHeap.allocPointerTo<EVP_PKEY>()
        val cert = nativeHeap.allocPointerTo<X509>()

        try {
            //TODO reading CA certificates
            require(PKCS12_parse(pkcs12, password?.concatToString(), pkey.ptr, cert.ptr, null) == 1) {
                "Failed to parse PKCS12 file"
            }
            block(pkey.value!!, cert.value!!)
        } catch (cause: Throwable) {
            EVP_PKEY_free(pkey.value)
            X509_free(cert.value)
            password?.fill('\u0000')
            throw cause
        }
    } finally {
        PKCS12_free(pkcs12)
    }
}

private fun <T> useFile(
    path: String,
    modes: String?,
    block: (CPointer<FILE>) -> T
): T {
    val file = requireNotNull(fopen(path, modes)) { "Failed to open file: $path" }
    try {
        return block(file)
    } finally {
        fclose(file)
    }
}

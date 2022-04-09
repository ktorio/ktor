/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.internal.openssl.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.posix.*

public class PKCS12Certificate(
    public val privateKey: CPointer<EVP_PKEY>,
    public val x509Certificate: CPointer<X509>
) : Closeable {

    override fun close() {
        EVP_PKEY_free(privateKey)
        X509_free(x509Certificate)
    }
}

internal fun PKCS12Certificate(certificatePath: String, certificatePassword: (() -> CharArray)?): PKCS12Certificate {
    val pkcs12 = useFile(certificatePath, "rb") { file ->
        requireNotNull(d2i_PKCS12_fp(file, null)) { "Failed to read PKCS12 file" }
    }
    try {
        val charPassword = certificatePassword?.invoke()
        val password = charPassword?.concatToString() ?: "" //empty password for openssl is absence of password
        charPassword?.fill('\u0000')

        val privateKey = nativeHeap.allocPointerTo<EVP_PKEY>()
        val x509Certificate = nativeHeap.allocPointerTo<X509>()

        try {
            //TODO reading CA certificates
            require(PKCS12_parse(pkcs12, password, privateKey.ptr, x509Certificate.ptr, null) == 1) {
                "Failed to parse PKCS12 file"
            }
            return PKCS12Certificate(privateKey.value!!, x509Certificate.value!!)
        } catch (cause: Throwable) {
            EVP_PKEY_free(privateKey.value)
            X509_free(x509Certificate.value)
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

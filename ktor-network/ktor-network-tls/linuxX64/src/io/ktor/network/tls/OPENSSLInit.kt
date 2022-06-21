/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.internal.openssl.*
import kotlinx.cinterop.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
internal val opensslInitReturnCode = opensslInitBridge() //TODO

private fun opensslInitBridge() {
    OPENSSL_init_crypto(OPENSSL_INIT_ADD_ALL_CIPHERS.convert(), null)
    OPENSSL_init_crypto(OPENSSL_INIT_ADD_ALL_DIGESTS.convert(), null)
    OPENSSL_init_ssl(0.convert(), null)
}

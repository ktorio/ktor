/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.cinterop.*
import openssl.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val opensslInitReturnCode = opensslInitBridge()

internal fun opensslInitBridge() {
    OPENSSL_init_crypto(OPENSSL_INIT_ADD_ALL_CIPHERS.convert(), null)
    OPENSSL_init_crypto(OPENSSL_INIT_ADD_ALL_DIGESTS.convert(), null)
    OPENSSL_init_ssl(0.convert(), null)
}

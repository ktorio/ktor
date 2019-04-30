/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import java.security.*

internal data class EncryptionInfo(
    val serverPublic: PublicKey,
    val clientPublic: PublicKey,
    val clientPrivate: PrivateKey
)

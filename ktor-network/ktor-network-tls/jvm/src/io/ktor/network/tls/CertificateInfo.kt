/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import javax.security.auth.x500.*

internal class CertificateInfo(
    val types: ByteArray,
    val hashAndSign: Array<HashAndSign>,
    val authorities: Set<X500Principal>
)

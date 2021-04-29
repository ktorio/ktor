/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.compression

import io.ktor.util.*

/**
 * Client content encoder.
 */
public interface ContentEncoder : Encoder {
    /**
     * Encoder identifier to use in http headers.
     */
    public val name: String
}

internal expect object GZipEncoder : ContentEncoder

internal expect object DeflateEncoder : ContentEncoder

internal object IdentityEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "identity"
}

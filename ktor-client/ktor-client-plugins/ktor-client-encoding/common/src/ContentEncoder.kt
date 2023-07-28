/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.compression

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Client content encoder.
 */
public interface ContentEncoder : Encoder {
    /**
     * Encoder identifier to use in http headers.
     */
    public val name: String
}

internal expect object GZipEncoder : ContentEncoder {
    override val name: String
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel
    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel
}

internal expect object DeflateEncoder : ContentEncoder {
    override val name: String
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel
    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel
}

internal object IdentityEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "identity"
}

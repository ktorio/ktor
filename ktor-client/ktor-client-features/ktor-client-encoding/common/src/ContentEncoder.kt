package io.ktor.client.features.compression

import io.ktor.util.*

/**
 * Client content encoder.
 */
interface ContentEncoder : Encoder {
    /**
     * Encoder identifier to use in http headers.
     */
    val name: String
}

internal expect object GZipEncoder : ContentEncoder

internal expect object DeflateEncoder : ContentEncoder

internal object IdentityEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "identity"
}

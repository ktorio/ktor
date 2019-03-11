package io.ktor.client.features.compression

import io.ktor.util.*

/**
 * Compression should be handled by any Js http api automatically.
 */

internal actual object GZipEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "gzip"
}

internal actual object DeflateEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "deflate"
}

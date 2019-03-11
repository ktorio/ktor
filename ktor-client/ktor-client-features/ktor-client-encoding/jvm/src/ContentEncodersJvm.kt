package io.ktor.client.features.compression

import io.ktor.util.*

internal actual object GZipEncoder : ContentEncoder, Encoder by GZip {
    override val name: String = "gzip"
}

internal actual object DeflateEncoder : ContentEncoder, Encoder by Deflate {
    override val name: String = "deflate"
}

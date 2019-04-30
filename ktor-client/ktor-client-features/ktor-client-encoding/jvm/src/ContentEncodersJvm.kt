/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.compression

import io.ktor.util.*

internal actual object GZipEncoder : ContentEncoder, Encoder by GZip {
    override val name: String = "gzip"
}

internal actual object DeflateEncoder : ContentEncoder, Encoder by Deflate {
    override val name: String = "deflate"
}

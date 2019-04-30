/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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

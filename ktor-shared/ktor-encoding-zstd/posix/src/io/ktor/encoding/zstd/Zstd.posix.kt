/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import io.ktor.util.Encoder
import io.ktor.util.Identity

public actual class ZstdEncoder : io.ktor.util.ContentEncoder, Encoder by Identity {
    actual override val name: String get() = "zstd"
}

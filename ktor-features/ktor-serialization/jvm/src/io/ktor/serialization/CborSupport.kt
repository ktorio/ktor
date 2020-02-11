/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.features.*
import io.ktor.http.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.modules.*

fun ContentNegotiation.Configuration.cbor(module: SerialModule = EmptyModule, encodeDefaults: Boolean = true) {
    serialization(
        ContentType.Application.Cbor,
        Cbor(context = module, encodeDefaults = encodeDefaults)
    )
}

/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.features.*
import io.ktor.http.*
import kotlinx.serialization.modules.*
import kotlinx.serialization.protobuf.*

/**
 * Register `application/protobuf` content type to [ContentNegotiation] feature using kotlinx.serialization.
 * @param module is used for serialization (optional)
 */
fun ContentNegotiation.Configuration.protoBuf(module: SerialModule = EmptyModule) {
    serialization(
        ContentType.Application.ProtoBuf,
        ProtoBuf(module)
    )
}

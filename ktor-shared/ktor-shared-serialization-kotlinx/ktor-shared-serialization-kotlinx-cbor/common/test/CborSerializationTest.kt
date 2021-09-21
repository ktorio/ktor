/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.shared.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.shared.serialization.kotlinx.cbor.*
import io.ktor.shared.serialization.kotlinx.test.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*

@OptIn(ExperimentalSerializationApi::class)
class CborSerializationTest : AbstractSerializationTest<Cbor>() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val defaultSerializationFormat: Cbor = DefaultCbor

    override fun assertEquals(expectedAsJson: String, actual: ByteArray, format: Cbor): Boolean {
        return expectedAsJson == actual.decodeToString()
    }
}

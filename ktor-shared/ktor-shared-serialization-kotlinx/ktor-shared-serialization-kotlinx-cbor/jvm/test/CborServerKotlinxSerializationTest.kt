/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.shared.serialization.kotlinx.test.cbor

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.shared.serialization.kotlinx.cbor.*
import io.ktor.shared.serialization.kotlinx.test.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
class CborServerKotlinxSerializationTest : AbstractServerSerializationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val customContentType: ContentType = ContentType.parse("application/x-cbor")

    override fun ContentNegotiation.Configuration.configureContentNegotiation(contentType: ContentType) {
        cbor(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultCbor.decodeFromByteArray(serializer, t)
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultCbor.encodeToByteArray(serializer, any)
    }
}

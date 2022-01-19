/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.serialization.kotlinx.test.cbor

import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
class CborServerKotlinxSerializationTest : AbstractServerSerializationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val customContentType: ContentType = ContentType.parse("application/x-cbor")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        cbor(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultCbor.decodeFromByteArray(serializer, t)
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultCbor.encodeToByteArray(serializer, any)
    }
}

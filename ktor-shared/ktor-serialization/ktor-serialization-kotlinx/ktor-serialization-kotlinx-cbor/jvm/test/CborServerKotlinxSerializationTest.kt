/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.cbor

import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.*
import java.nio.charset.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class CborServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val customContentType: ContentType = ContentType.parse("application/x-cbor")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        cbor(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return DefaultCbor.decodeFromByteArray(serializer, t)
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return DefaultCbor.decodeFromByteArray(listSerializer, t)
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return DefaultCbor.encodeToByteArray(serializer, any)
    }

    @Ignore
    override fun testMap() {
    }

    @Ignore
    override fun testReceiveNullValue() {
    }

    @Ignore
    override fun testFlowNoAcceptUtf8() {
    }

    @Ignore
    override fun testFlowAcceptUtf16() {
    }
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.contentnegotiation.*
import java.nio.charset.*
import kotlin.test.*

class JsonServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, KotlinxSerializationConverter(DefaultJson))
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return DefaultJson.decodeFromString(serializer, String(t))
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return DefaultJson.decodeFromString(listSerializer, String(t, charset))
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return DefaultJson.encodeToString(serializer, any).toByteArray()
    }

    @Ignore
    override fun testMap() {
    }

    @Ignore
    override fun testFlowAcceptUtf16() {
    }
}

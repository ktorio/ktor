/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

class XmlServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        xml(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return DefaultXml.decodeFromString(serializer, String(t))
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return DefaultXml.decodeFromString(listSerializer, String(t, charset))
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return DefaultXml.encodeToString(serializer, any).toByteArray()
    }

    @Ignore
    override fun testMap() {
    }

    @Ignore
    override fun testFlowNoAcceptUtf8() {
    }

    @Ignore
    override fun testFlowAcceptUtf16() {
    }

    @Ignore
    override fun testReceiveNullValue() {
    }
}

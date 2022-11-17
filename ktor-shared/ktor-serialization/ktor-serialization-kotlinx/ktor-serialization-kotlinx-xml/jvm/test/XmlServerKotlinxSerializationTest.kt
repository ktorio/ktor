/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.plugins.contentnegotiation.*
import java.nio.charset.*

class XmlServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(
        contentType: ContentType,
        streamRequestBody: Boolean,
        prettyPrint: Boolean
    ) {
        xml(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultXml.decodeFromString(serializer, String(t))
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset, prettyPrint: Boolean): List<TestEntity> {
        return DefaultXml.decodeFromString(listSerializer, String(t, charset))
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultXml.encodeToString(serializer, any).toByteArray()
    }
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.http.*
import io.ktor.server.features.*
import io.ktor.shared.serialization.kotlinx.*

class ServerJsonTest : AbstractServerSerializationTest() {
    override val testContentType: ContentType = ContentType.Application.Json

    override fun ContentNegotiation.Configuration.configureContentNegotiation() {
        json()
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultJson.decodeFromString(serializer, String(t))
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultJson.encodeToString(serializer, any).toByteArray()
    }
}

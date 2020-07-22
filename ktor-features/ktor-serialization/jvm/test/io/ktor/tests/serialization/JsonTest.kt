/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.serialization

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.serialization.json.*

class JsonTest : AbstractSerializationTest() {
    override val testContentType: ContentType = ContentType.Application.Json

    override fun ContentNegotiation.Configuration.configureContentNegotiation() {
        json()
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultJsonConfiguration.decodeFromString(serializer, String(t))
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultJsonConfiguration.encodeToString(serializer, any).toByteArray()
    }
}

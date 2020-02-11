/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.serialization

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.serialization.cbor.*

class CborTest : AbstractSerializationTest() {
    override val testContentType: ContentType = ContentType.Application.Cbor

    override fun ContentNegotiation.Configuration.configureContentNegotiation() {
        cbor()
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return Cbor.load(serializer, t)
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return Cbor.dump(serializer, any)
    }
}

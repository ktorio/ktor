/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor.tests

import io.ktor.client.features.cbor.*
import io.ktor.client.features.cbor.serializer.*

class DefaultSerializerCborTest : CborTest() {
    // Force CborFeature to use defaultSerializer()
    override val serializerImpl: CborSerializer = KotlinxSerializer()
}

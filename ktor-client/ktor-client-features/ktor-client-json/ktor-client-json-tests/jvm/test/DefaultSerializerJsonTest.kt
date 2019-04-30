/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.tests

import io.ktor.client.features.json.*

class DefaultSerializerJsonTest : JsonTest() {
    // Force JsonFeature to use defaultSerializer()
    override val serializerImpl: JsonSerializer = GsonSerializer()
}

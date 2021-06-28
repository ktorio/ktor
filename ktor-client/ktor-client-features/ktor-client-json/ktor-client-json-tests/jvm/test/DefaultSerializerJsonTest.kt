/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.features.json.*
import io.ktor.client.features.json.tests.*

class DefaultSerializerJsonTest : JsonTest() {
    // Force JsonFeature to use defaultSerializer()
    override val serializerImpl: JsonSerializer = GsonSerializer()
}

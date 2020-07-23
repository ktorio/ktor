/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.tests

import com.squareup.moshi.kotlin.reflect.*
import io.ktor.client.features.json.*

class MoshiTest: JsonTest() {
    @ExperimentalStdlibApi
    override val serializerImpl = MoshiSerializer {
        add(KotlinJsonAdapterFactory())
    }
}

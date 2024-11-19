/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.serialization.gson.*
import kotlin.test.*

class GsonContentNegotiationTest : JsonContentNegotiationTest(GsonConverter()) {
    @Ignore
    override fun testJsonWithNullValue() {}
}

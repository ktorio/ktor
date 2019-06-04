/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import kotlin.test.*

class DefaultEngineTest {
    @Test
    @Ignore
    fun instantiationTest() {
        val client = HttpClient()
        client.close()
    }
}

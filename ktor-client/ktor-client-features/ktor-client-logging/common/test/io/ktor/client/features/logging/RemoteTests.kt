/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class RemoteTests : ClientLoader() {
    @Test
    fun testDownloadWithNoneLogLevel() = clientTests {
        config {
            install(Logging) {
                level = LogLevel.NONE
            }
        }

        test { client ->
            val size = 4 * 1024 * 1024
            client.get<HttpStatement>("$TEST_SERVER/bytes?size=$size").execute {
                assertEquals(size, it.readBytes().size)
            }
        }
    }
}

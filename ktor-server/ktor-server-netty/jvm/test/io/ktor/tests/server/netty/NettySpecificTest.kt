/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.test.*

class NettySpecificTest {

    @Test
    fun testNoLeakWithoutStartAndStop() {
        repeat(100000) {
            embeddedServer(Netty, applicationEngineEnvironment {  })
        }
    }
}

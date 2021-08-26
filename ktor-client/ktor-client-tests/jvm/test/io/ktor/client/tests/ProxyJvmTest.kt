/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.tests.utils.*
import java.net.*
import kotlin.test.*


class ProxyJvmTest : ClientLoader() {

    @Test
    fun canUseSocksProxy() = clientTests(skipEngines = listOf("Apache")) {
        config {
            engine {
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(9090))
            }
        }
    }
}

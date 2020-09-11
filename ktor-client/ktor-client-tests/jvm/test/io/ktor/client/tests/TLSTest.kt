/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class TLSTest : ClientLoader() {

    @Test
    fun clientCertificateRequest() = testWithEngine(CIO) {
        test { client ->
            client.get<String>("https://devstack.vwgroup.com/bitbucket")
        }
    }
}

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.tests.*

class Apache5HttpClientTest : HttpClientTest(Apache5)

class Apache5SslOverProxyTest : SslOverProxyTest<Apache5EngineConfig>(Apache5) {

    override fun Apache5EngineConfig.disableCertificatePinning() {
        this.sslContext = this@Apache5SslOverProxyTest.unsafeSslContext
    }
}

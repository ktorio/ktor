/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.netty

import io.ktor.client.tests.*

class NettyHttpsTest : HttpsTest<NettyHttpConfig>(Netty) {

    override fun NettyHttpConfig.disableCertificatePinning() {
//        config {
        sslContext(unsafeSslContext)
//        }
    }
}

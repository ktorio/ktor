/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.engine.curl.*
import io.ktor.client.tests.*

class CurlHttp2Test : Http2Test<CurlClientEngineConfig>(Curl, useH2c = false) {

    override fun CurlClientEngineConfig.disableCertificateValidation() {
        sslVerify = false
    }
}

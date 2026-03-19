/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.engine.curl.*
import io.ktor.client.tests.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class CurlHttp2Test : Http2Test<CurlClientEngineConfig>(Curl, useH2c = false) {

    override fun CurlClientEngineConfig.disableCertificateValidation() {
        sslVerify = false
    }

    // https://github.com/ktorio/ktor/issues/5458
    // The Windows CI curl build does not negotiate HTTP/2 via ALPN,
    // so the response version is always HTTP/1.1 on mingwX64.
    @OptIn(ExperimentalNativeApi::class)
    @Test
    override fun `test protocol version is HTTP 2`() {
        if (Platform.osFamily == OsFamily.WINDOWS) return
        super.`test protocol version is HTTP 2`()
    }
}

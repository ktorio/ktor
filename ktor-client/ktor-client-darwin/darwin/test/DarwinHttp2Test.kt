/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.darwin.utils.*
import io.ktor.client.tests.*
import kotlinx.cinterop.UnsafeNumber

class DarwinHttp2Test : Http2Test<DarwinClientEngineConfig>(Darwin, useH2c = false) {
    @OptIn(UnsafeNumber::class)
    override fun DarwinClientEngineConfig.disableCertificateValidation() {
        handleChallenge { _, _, challenge, completionHandler -> trustAnyCertificate(challenge, completionHandler) }
    }
}

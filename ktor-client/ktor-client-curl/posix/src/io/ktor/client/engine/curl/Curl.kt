/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import kotlinx.cinterop.*
import libcurl.*
import kotlin.native.SharedImmutable

// This function is thread unsafe!
// The man page asks to run it once per program,
// while the program "is still single threaded", explicitly stating that
// it should not called while any other thread is running.
// See the curl_global_init(3) man page for details.
@SharedImmutable
private val curlGlobalInitReturnCode = curl_global_init(CURL_GLOBAL_ALL.convert())

private val initHook = Curl

/**
 * [HttpClientEngineFactory] using a curl library in implementation
 * with the the associated configuration [HttpClientEngineConfig].
 */
object Curl : HttpClientEngineFactory<HttpClientEngineConfig> {
    init {
        engines.append(this)
    }

    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        @Suppress("DEPRECATION")
        if (curlGlobalInitReturnCode != 0U) {
            throw CurlRuntimeException("curl_global_init() returned non-zero verify: $curlGlobalInitReturnCode")
        }

        return CurlClientEngine(CurlClientEngineConfig().apply(block))
    }

    override fun toString() = "Curl"
}

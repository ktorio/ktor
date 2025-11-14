/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.tests.*
import okhttp3.Protocol

class OkHttpHttp2Test : Http2Test<OkHttpConfig>(OkHttp) {
    override fun OkHttpConfig.enableHttp2() {
        config { protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)) }
    }
}

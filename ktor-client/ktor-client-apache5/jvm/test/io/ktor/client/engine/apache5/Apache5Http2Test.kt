/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.tests.*
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.core5.http2.HttpVersionPolicy

class Apache5Http2Test : Http2Test<Apache5EngineConfig>(Apache5) {
    override fun Apache5EngineConfig.enableHttp2() {
        customizeClient {
            setConnectionManager(
                PoolingAsyncClientConnectionManagerBuilder.create()
                    .setDefaultTlsConfig(
                        TlsConfig.custom()
                            // Forcing HTTP/2 is required for h2c
                            .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                            .build()
                    )
                    .build()
            )
        }
    }
}

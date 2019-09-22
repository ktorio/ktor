/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import platform.Foundation.*

/**
 * Custom [IosClientEngine] config.
 */
class IosClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Request configuration.
     */
    var requestConfig: NSMutableURLRequest.() -> Unit = {}

    /**
     * Session configuration.
     */
    var sessionConfig: NSURLSessionConfiguration.() -> Unit = {}

    /**
     * Append block with [NSMutableURLRequest] configuration to [requestConfig].
     */
    fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        requestConfig = {
            old()
            block()
        }
    }

    /**
     * Append block with [NSURLSessionConfiguration] configuration to [sessionConfig].
     */
    fun configureSession(block: NSURLSessionConfiguration.() -> Unit) {
        val old = sessionConfig

        sessionConfig = {
            old()
            block()
        }
    }
}

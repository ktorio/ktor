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
    internal var requestConfig: NSMutableURLRequest.() -> Unit = {}

    /**
     * Provide [NSMutableURLRequest] requestConfig.
     */
    fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        requestConfig = {
            old()
            block()
        }
    }
}

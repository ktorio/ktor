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

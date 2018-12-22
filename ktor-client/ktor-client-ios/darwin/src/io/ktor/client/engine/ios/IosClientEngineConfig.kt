package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import platform.Foundation.*

/**
 * Custom [IosClientEngine] config.
 */
class IosClientEngineConfig : HttpClientEngineConfig() {
    private var configuration: NSMutableURLRequest.() -> Unit = {}

    /**
     * Provide [NSMutableURLRequest] configuration.
     */
    fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = configuration

        configuration = {
            old()
            block()
        }
    }
}

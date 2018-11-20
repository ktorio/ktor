package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import platform.Foundation.*

class IosClientEngineConfig : HttpClientEngineConfig() {

    private var configuration: NSMutableURLRequest.() -> Unit = {}

    fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = configuration

        configuration =  {
            old()
            block()
        }
    }
}
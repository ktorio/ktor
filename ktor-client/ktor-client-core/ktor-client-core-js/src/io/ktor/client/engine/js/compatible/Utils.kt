package io.ktor.client.engine.js.compatible

import io.ktor.client.engine.js.compatible.browser.BrowserUtils
import io.ktor.client.engine.js.compatible.node.NodeUtils
import kotlinx.coroutines.io.ByteChannel
import kotlinx.coroutines.io.ByteReadChannel
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.coroutines.CoroutineContext
import kotlin.js.Promise

abstract class Utils {
    companion object {
        fun get(): Utils {
            return if (ENV.isNode()) { NodeUtils } else { BrowserUtils }
        }
    }

    abstract fun fetch(input: dynamic, init: RequestInit): Promise<Response>
    abstract fun getBodyContentAsChannel(resp: Response, context: CoroutineContext): ByteReadChannel
}

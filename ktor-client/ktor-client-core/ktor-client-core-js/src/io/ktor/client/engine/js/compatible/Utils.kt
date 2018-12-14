package io.ktor.client.engine.js.compatible

import io.ktor.client.engine.js.compatible.browser.*
import io.ktor.client.engine.js.compatible.node.*
import kotlinx.coroutines.io.*
import org.w3c.fetch.*
import kotlin.coroutines.*
import kotlin.js.*


abstract class Utils {
    companion object {
        fun get(): Utils {
            return if (hasFetchApi()) {
                return BrowserUtils
            } else NodeUtils
        }

        private fun hasFetchApi(): Boolean {
            return js("typeof window") !== "undefined"
        }
    }

    abstract fun fetch(input: String, init: RequestInit): Promise<Response>

    abstract fun getBodyContentAsChannel(resp: Response, context: CoroutineContext): ByteReadChannel

}

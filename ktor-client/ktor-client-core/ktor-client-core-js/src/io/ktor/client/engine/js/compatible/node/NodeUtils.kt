package io.ktor.client.engine.js.compatible.node

import io.ktor.client.engine.js.compatible.Utils
import kotlinx.coroutines.io.ByteReadChannel
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.coroutines.CoroutineContext
import kotlin.js.Promise

object NodeUtils : Utils() {
    override fun getBodyContentAsChannel(resp: Response, context: CoroutineContext): ByteReadChannel {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override fun fetch(input: dynamic, init: RequestInit): Promise<Response> {

        // Not using @Jsmodule because node-fetch is not required in browser
        val nodeFetch: dynamic = jeRequire("node-fetch")
        return nodeFetch(input, init)
    }

    private fun jeRequire(moduleName: String): dynamic {
        try {
            return js("require(moduleName)")
        } catch (e: dynamic) {
            throw Error("Error loading module '$moduleName'.")
        }
    }

}

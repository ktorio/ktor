package io.ktor.client.engine.js.compatible.node

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.compatible.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.coroutines.*
import kotlin.js.*

object NodeUtils : Utils() {
    override fun getBodyContentAsChannel(
        resp: Response,
        context: CoroutineContext
    ): ByteReadChannel =
            writer(context) {
                val buffer = suspendCancellableCoroutine<ArrayBuffer> { con ->
                    resp.arrayBuffer()
                            .then { con.resume(it) }
                            .catch { con.resumeWithException(it) }
                }
                val byteArray = Uint8Array(buffer).asByteArray()
                channel.writeFully(byteArray)
            }.channel

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override fun fetch(input: String, init: RequestInit): Promise<Response> {

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

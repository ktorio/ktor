package io.ktor.client.engine.js.compatible.node

import io.ktor.client.engine.js.asByteArray
import io.ktor.client.engine.js.compatible.Utils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.io.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise


object NodeUtils : Utils() {
    override fun getBodyContentAsChannel(
            resp: Response,
            context: CoroutineContext
    ): ByteReadChannel =
            GlobalScope.writer {
                val buffer = suspendCancellableCoroutine<ArrayBuffer> { con ->
                    resp.arrayBuffer()
                            .then { con.resume(it) }
                            .catch { con.resumeWithException(it) }
                }
                val byteArray = Uint8Array(buffer).asByteArray()
                channel.writeFully(byteArray)
            }.channel

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

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.*

@OptIn(UnsafeNumber::class)
internal class DarwinLegacySession(
    private val config: DarwinLegacyClientEngineConfig,
    private val requestQueue: NSOperationQueue
) : Closeable {
    private val closed = atomic(false)
    private val responseReader = DarwinLegacyResponseReader(config)

    private val session: NSURLSession = config.preconfiguredSession ?: createSession()

    internal suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData {
        val nativeRequest = request.toNSUrlRequest()
            .apply(config.requestConfig)
        val task = session.dataTaskWithRequest(nativeRequest)

        val result: CompletableDeferred<HttpResponseData> = responseReader.read(request, callContext, task)
        task.resume()

        try {
            return result.await()
        } catch (cause: Throwable) {
            if (task.state == NSURLSessionTaskStateRunning) task.cancel()
            throw cause
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        session.finishTasksAndInvalidate()
    }

    private fun createSession(): NSURLSession {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            setupProxy(config)
            setHTTPCookieStorage(null)

            config.sessionConfig(this)
        }

        return NSURLSession.sessionWithConfiguration(
            configuration,
            responseReader,
            delegateQueue = requestQueue
        )
    }
}

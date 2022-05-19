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
internal class DarwinSession(
    config: DarwinClientEngineConfig,
    requestQueue: NSOperationQueue
) : Closeable {
    private val closed = atomic(false)

    private val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
        setupProxy(config)
        setHTTPCookieStorage(null)

        config.sessionConfig(this)
    }

    private val responseReader = DarwinResponseReader(config)

    private val session = NSURLSession.sessionWithConfiguration(
        configuration,
        responseReader,
        delegateQueue = requestQueue
    )

    internal suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData {
        val nativeRequest = request.toNSUrlRequest()
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
}

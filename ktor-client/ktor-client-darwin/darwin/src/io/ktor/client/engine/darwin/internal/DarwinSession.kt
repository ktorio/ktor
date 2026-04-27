/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.job
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTaskStateRunning
import kotlin.coroutines.CoroutineContext

@OptIn(UnsafeNumber::class)
internal class DarwinSession(
    private val config: DarwinClientEngineConfig,
    requestQueue: NSOperationQueue?
) : Closeable {
    private val closed = atomic(false)
    private val sessionLock = SynchronizedObject()

    private val sessionAndDelegate = config.sessionAndDelegate ?: createSession(config, requestQueue)
    private val session = sessionAndDelegate.first
    private val delegate = sessionAndDelegate.second

    @OptIn(InternalAPI::class, ExperimentalForeignApi::class)
    internal suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData {
        val nativeRequest = request.toNSUrlRequest()
            .apply(config.requestConfig)
        val (task, response) = if (request.isUpgradeRequest()) {
            val task = withSession { webSocketTaskWithRequest(nativeRequest) }
            val response = delegate.read(request, task, callContext)
            task to response
        } else {
            val task = withSession { dataTaskWithRequest(nativeRequest) }
            val response = delegate.read(request, callContext, task)
            task to response
        }

        callContext.job.invokeOnCompletion { cause ->
            if (cause != null) {
                task.cancel()
            }
        }

        task.resume()

        try {
            return response.await()
        } catch (cause: Throwable) {
            if (task.state == NSURLSessionTaskStateRunning) task.cancel()
            throw cause
        }
    }

    private inline fun <T> withSession(block: NSURLSession.() -> T): T {
        cancelIfClosed()
        return synchronized(sessionLock) {
            cancelIfClosed()
            block(session)
        }
    }

    override fun close() {
        if (closed.value) return
        synchronized(sessionLock) {
            if (!closed.compareAndSet(expect = false, update = true)) return
            session.finishTasksAndInvalidate()
        }
    }

    private fun cancelIfClosed() {
        if (closed.value) throw CancellationException("Darwin session is closed")
    }
}

@OptIn(UnsafeNumber::class)
internal fun createSession(
    config: DarwinClientEngineConfig,
    requestQueue: NSOperationQueue?
): Pair<NSURLSession, KtorNSURLSessionDelegate> {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
        setupProxy(config)
        setHTTPCookieStorage(null)

        config.sessionConfig(this)
    }
    val delegate = KtorNSURLSessionDelegate(config.challengeHandler)

    return NSURLSession.sessionWithConfiguration(
        configuration,
        delegate,
        delegateQueue = requestQueue
    ) to delegate
}

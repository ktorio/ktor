/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.util.Attributes
import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import libcurl.curl_slist_append
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class CurlMultiApiHandlerTest {

    @OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
    @Test
    fun `cancelling a request releases its stable response data`() {
        val handler = CurlMultiApiHandler()
        try {
            val requestReference = scheduleAndCancel(handler)

            repeat(10) {
                GC.collect()
                if (requestReference.get() == null) return
            }

            assertNull(requestReference.get())
        } finally {
            handler.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
    private fun scheduleAndCancel(handler: CurlMultiApiHandler): WeakReference<CurlRequestData> {
        val request = CurlRequestData(
            protocol = "http",
            url = "http://127.0.0.1:1/",
            method = "GET",
            headers = checkNotNull(curl_slist_append(null, "Expect:")),
            proxy = null,
            content = ByteReadChannel.Empty,
            contentLength = 0,
            connectTimeout = null,
            callContext = Job(),
            isUpgradeRequest = false,
            forceProxyTunneling = false,
            sslVerify = true,
            caInfo = null,
            caPath = null,
            attributes = Attributes(),
        )
        val requestReference = WeakReference(request)
        val response = CompletableDeferred<CurlSuccess>()
        val easyHandle = handler.scheduleRequest(request, response)
        val cancellationCause = CancellationException("Test cancellation")
        var completionCause: Throwable? = null
        response.invokeOnCompletion { completionCause = it }

        handler.cancelRequest(easyHandle, cancellationCause)
        memScoped {
            handler.perform(alloc<IntVar>())
        }

        assertSame(cancellationCause, completionCause)
        return requestReference
    }
}

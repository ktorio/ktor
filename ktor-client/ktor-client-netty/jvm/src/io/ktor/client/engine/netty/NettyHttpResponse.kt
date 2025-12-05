/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.netty

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.future.*
import java.net.http.*
import kotlin.coroutines.*

internal suspend fun HttpClient.executeHttpRequest(
    callContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData? {
    val httpRequest = requestData.convertToHttpRequest(callContext)
    return try {
        sendAsync(httpRequest, NettyHttpResponseBodyHandler(callContext, requestData))?.await()?.body()
    } catch (cause: HttpConnectTimeoutException) {
        throw ConnectTimeoutException(requestData, cause)
    } catch (_: HttpTimeoutException) {
        throw HttpRequestTimeoutException(requestData)
    }
}

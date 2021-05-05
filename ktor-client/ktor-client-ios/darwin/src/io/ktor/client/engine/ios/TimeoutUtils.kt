/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.ios

import io.ktor.client.features.*
import io.ktor.client.request.*
import platform.Foundation.*

/**
 * Update [NSMutableURLRequest] and setup timeout interval that equal to socket interval specified by [HttpTimeout].
 */
internal fun NSMutableURLRequest.setupSocketTimeout(requestData: HttpRequestData) {
    // iOS timeout works like a socket timeout.
    requestData.getCapabilityOrNull(HttpTimeout)?.socketTimeoutMillis?.let {
        if (it != HttpTimeout.INFINITE_TIMEOUT_MS) {
            // Timeout should be specified in seconds.
            setTimeoutInterval(it / 1000.0)
        } else {
            setTimeoutInterval(Double.MAX_VALUE)
        }
    }
}

internal fun handleNSError(requestData: HttpRequestData, error: NSError): Throwable = when (error.code) {
    NSURLErrorTimedOut -> SocketTimeoutException(requestData)
    else -> IosHttpRequestException(error)
}

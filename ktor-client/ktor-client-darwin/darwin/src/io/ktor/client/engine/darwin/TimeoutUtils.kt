/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.darwin

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import platform.Foundation.*
import kotlin.time.*

/**
 * Update [NSMutableURLRequest] and setup timeout interval that equal to socket interval specified by [HttpTimeout].
 */
internal fun NSMutableURLRequest.setupSocketTimeout(requestData: HttpRequestData) {
    // Darwin timeout works like a socket timeout.
    requestData.getCapabilityOrNull(HttpTimeout)?.socketTimeout?.let {
        if (it != Duration.INFINITE) {
            // Timeout should be specified in seconds.
            setTimeoutInterval(it.toDouble(DurationUnit.SECONDS))
        } else {
            setTimeoutInterval(Double.MAX_VALUE)
        }
    }
}

internal fun handleNSError(requestData: HttpRequestData, error: NSError): Throwable = when (error.code) {
    NSURLErrorTimedOut -> SocketTimeoutException(requestData)
    else -> DarwinHttpRequestException(error)
}

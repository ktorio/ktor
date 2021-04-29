/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import com.facebook.stetho.inspector.network.*
import io.ktor.client.request.*

/**
 * Implementation of [NetworkEventReporter.InspectorResponse] that is built to work with [StethoTracer].
 */
internal class KtorInterceptorResponse(
    private val requestId: String,
    private val requestData: HttpRequestData,
    private val responseData: HttpResponseData
) : NetworkEventReporter.InspectorResponse,
    NetworkEventReporter.InspectorHeaders by KtorInterceptorHeaders(responseData.headers) {

    override fun requestId(): String {
        return requestId
    }

    override fun reasonPhrase(): String {
        return responseData.statusCode.description
    }

    override fun url(): String {
        return requestData.url.toString()
    }

    override fun connectionReused(): Boolean {
        return false
    }

    override fun fromDiskCache(): Boolean {
        return false
    }

    override fun connectionId(): Int {
        return 0
    }

    override fun statusCode(): Int {
        return responseData.statusCode.value
    }
}

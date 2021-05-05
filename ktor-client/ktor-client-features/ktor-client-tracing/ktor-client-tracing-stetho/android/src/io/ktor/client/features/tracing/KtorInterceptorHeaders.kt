/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import com.facebook.stetho.inspector.network.*
import io.ktor.http.*
import java.util.*

/**
 * Implementation of [NetworkEventReporter.InspectorHeaders] to be reused in [KtorInterceptorRequest] and
 * [KtorInterceptorResponse]. Uses provided Ktor [Headers] as a source of HTTP headers.
 */
internal class KtorInterceptorHeaders(
    private val headers: Headers
) : NetworkEventReporter.InspectorHeaders {

    /**
     * Ordered list of header names to be used to get index-based access to headers.
     */
    private val headerNames: List<String>

    init {
        headerNames = mutableListOf<String>().apply {
            headers.forEach { key, _ -> add(key) }
        }
    }

    override fun headerValue(index: Int): String {
        return headers[headerName(index)]!!
    }

    override fun headerName(index: Int): String {
        return headerNames[index]
    }

    override fun headerCount(): Int {
        return headerNames.size
    }

    override fun firstHeaderValue(name: String?): String? {
        headerNames.forEach { headerName ->
            if (headerName.toLowerCase(Locale.ROOT) == name?.toLowerCase(Locale.ROOT)) {
                return headers[headerName]
            }
        }

        return null
    }
}

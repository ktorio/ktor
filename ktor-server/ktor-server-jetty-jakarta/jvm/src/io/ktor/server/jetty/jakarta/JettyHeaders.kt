/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.Headers
import org.eclipse.jetty.server.Request

internal class JettyHeaders(
    private val jettyRequest: Request
) : Headers {
    override val caseInsensitiveName: Boolean = true

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return jettyRequest.headers.fieldNamesCollection.map {
            object : Map.Entry<String, List<String>> {
                override val key: String = it
                override val value: List<String> = jettyRequest.headers.getValuesList(it)
            }
        }.toSet()
    }

    override fun getAll(name: String): List<String>? = jettyRequest.headers.getValuesList(name).takeIf {
        it.isNotEmpty()
    }

    override fun get(name: String): String? = jettyRequest.headers.get(name).takeIf { it.isNotEmpty() }

    override fun isEmpty(): Boolean = jettyRequest.headers.size() == 0

    override fun names(): Set<String> = jettyRequest.headers.fieldNamesCollection
}

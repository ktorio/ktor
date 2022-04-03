/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

internal fun Appendable.logHeaders(headers: Set<Map.Entry<String, List<String>>>) {
    val sortedHeaders = headers.toList().sortedBy { it.key }

    sortedHeaders.forEach { (key, values) ->
        logHeader(key, values.joinToString("; "))
    }
}

internal fun Appendable.logHeader(key: String, value: String) {
    appendLine("-> $key: $value")
}

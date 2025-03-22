/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*

/**
 * An adapter from CIO low-level headers map to ktor [Headers] interface
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.CIOHeaders)
 */
public class CIOHeaders(private val headers: HttpHeadersMap) : Headers {

    private val names: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        LinkedHashSet<String>(headers.size).apply {
            for (offset in headers.offsets()) {
                add(headers.nameAtOffset(offset).toString())
            }
        }
    }

    override val caseInsensitiveName: Boolean get() = true

    override fun names(): Set<String> = names
    override fun get(name: String): String? = headers[name]?.toString()

    override fun getAll(name: String): List<String>? =
        headers.getAll(name).map { it.toString() }.toList().takeIf { it.isNotEmpty() }

    override fun isEmpty(): Boolean = headers.size == 0
    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return headers.offsets().map { idx -> Entry(idx) }.toSet()
    }

    private inner class Entry(private val offset: Int) : Map.Entry<String, List<String>> {
        override val key: String get() = headers.nameAtOffset(offset).toString()
        override val value: List<String> get() = listOf(headers.valueAtOffset(offset).toString())
    }
}

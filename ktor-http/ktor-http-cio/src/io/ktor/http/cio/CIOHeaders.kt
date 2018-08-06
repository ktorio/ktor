package io.ktor.http.cio

import io.ktor.http.*

class CIOHeaders(private val headers: HttpHeadersMap) : Headers {
    private val names: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        LinkedHashSet<String>(headers.size).apply {
            for (i in 0 until headers.size) {
                add(headers.nameAt(i).toString())
            }
        }
    }

    override val caseInsensitiveName: Boolean get() = true

    override fun names() = names
    override fun get(name: String): String? = headers[name]?.toString()

    override fun getAll(name: String): List<String> = headers.getAll(name).map { it.toString() }.toList()

    override fun isEmpty() = headers.size == 0
    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return (0 until headers.size).map { idx -> Entry(idx) }.toSet()
    }

    private inner class Entry(private val idx: Int) : Map.Entry<String, List<String>> {
        override val key: String get() = headers.nameAt(idx).toString()
        override val value: List<String> get() = listOf(headers.valueAt(idx).toString())
    }
}
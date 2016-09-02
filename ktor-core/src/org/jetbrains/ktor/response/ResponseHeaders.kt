package org.jetbrains.ktor.response

import org.jetbrains.ktor.util.*

abstract class ResponseHeaders {
    operator fun contains(name: String): Boolean = getHostHeaderValues(name).isNotEmpty()
    operator fun get(name: String): String? = getHostHeaderValues(name).firstOrNull()
    fun values(name: String): List<String> = getHostHeaderValues(name)
    fun allValues(): ValuesMap = getHostHeaderNames().fold(ValuesMapBuilder(true)) { builder, headerName ->
        builder.appendAll(headerName, getHostHeaderValues(headerName))
        builder
    }.build()

    fun append(name: String, value: String) {
        hostAppendHeader(name, value)
    }

    protected abstract fun hostAppendHeader(name: String, value: String)
    protected abstract fun getHostHeaderNames(): List<String>
    protected abstract fun getHostHeaderValues(name: String): List<String>
}
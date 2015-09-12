package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*

public abstract class ResponseHeaders {
    private val headersChain = Interceptable2<String, String, Unit> { name, value ->
        hostAppendHeader(name, value)
    }

    public fun contains(name: String): Boolean = getHostHeaderValues(name).isNotEmpty()
    public fun get(name: String): String? = getHostHeaderValues(name).firstOrNull()
    public fun values(name: String): List<String> = getHostHeaderValues(name)
    public fun allValues(): ValuesMap = getHostHeaderNames().fold(ValuesMap.Builder()) { builder, headerName ->
        builder.appendAll(headerName, getHostHeaderValues(headerName))
        builder
    }.build()

    public fun append(name: String, value: String) {
        headersChain.call(name, value)
    }

    public final fun intercept(handler: (name: String, value: String, next: (name: String, value: String) -> Unit) -> Unit) {
        headersChain.intercept(handler)
    }

    protected abstract fun hostAppendHeader(name: String, value: String)
    protected abstract fun getHostHeaderNames(): List<String>
    protected abstract fun getHostHeaderValues(name: String): List<String>
}
package io.ktor.response

import io.ktor.http.*

abstract class ResponseHeaders {
    operator fun contains(name: String): Boolean = get(name) != null
    open operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()
    fun values(name: String): List<String> = getEngineHeaderValues(name)
    fun allValues(): Headers = Headers.build {
        getEngineHeaderNames().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    fun append(name: String, value: String, safeOnly: Boolean = true) {
        if (safeOnly && HttpHeaders.isUnsafe(name))
            throw UnsafeHeaderException(name)
        engineAppendHeader(name, value)
    }

    protected abstract fun engineAppendHeader(name: String, value: String)
    protected abstract fun getEngineHeaderNames(): List<String>
    protected abstract fun getEngineHeaderValues(name: String): List<String>
}


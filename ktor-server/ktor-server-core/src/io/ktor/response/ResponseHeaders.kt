package io.ktor.response

import io.ktor.http.*

abstract class ResponseHeaders {
    operator fun contains(name: String): Boolean = getEngineHeaderValues(name).isNotEmpty()
    operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()
    fun values(name: String): List<String> = getEngineHeaderValues(name)
    fun allValues(): Headers = Headers.build {
        getEngineHeaderNames().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    fun append(name: String, value: String, safeOnly: Boolean = true) {
        if (safeOnly && name.isUnsafe())
            throw UnsafeHeaderException(name)
        engineAppendHeader(name, value)
    }

    protected abstract fun engineAppendHeader(name: String, value: String)
    protected abstract fun getEngineHeaderNames(): List<String>
    protected abstract fun getEngineHeaderValues(name: String): List<String>
}

private fun String.isUnsafe(): Boolean = unsafeHeaders.any { it.equals(this, ignoreCase = true) }

private val unsafeHeaders = setOf(
        HttpHeaders.ContentLength,
        HttpHeaders.ContentType,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Upgrade
)

class UnsafeHeaderException(header: String) : IllegalArgumentException("Header $header is controlled by the engine and cannot be set explicitly")

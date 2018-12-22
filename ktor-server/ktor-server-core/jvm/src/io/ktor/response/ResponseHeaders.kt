package io.ktor.response

import io.ktor.http.*

/**
 * Server's response headers
 */
abstract class ResponseHeaders {
    /**
     * Check if there is response HTTP header with [name] set
     */
    operator fun contains(name: String): Boolean = get(name) != null

    /**
     * Find first response HTTP header with [name] or return `null`
     */
    open operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()

    /**
     * Find all response HTTP header values for [name]
     */
    fun values(name: String): List<String> = getEngineHeaderValues(name)

    /***
     * Build a [Headers] instance from response HTTP header values
     */
    fun allValues(): Headers = Headers.build {
        getEngineHeaderNames().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    /**
     * Append HTTP response header
     * @param safeOnly `true` by default, prevents from setting unsafe headers
     */
    fun append(name: String, value: String, safeOnly: Boolean = true) {
        if (safeOnly && HttpHeaders.isUnsafe(name))
            throw UnsafeHeaderException(name)
        engineAppendHeader(name, value)
    }

    /**
     * Engine's header appending implementation
     */
    protected abstract fun engineAppendHeader(name: String, value: String)

    /**
     * Engine's response header names extractor
     */
    protected abstract fun getEngineHeaderNames(): List<String>

    /**
     * Engine's response header values extractor
     */
    protected abstract fun getEngineHeaderValues(name: String): List<String>
}


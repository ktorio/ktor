/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.response

import io.ktor.http.*

/**
 * Server's response headers
 */
public abstract class ResponseHeaders {
    /**
     * Check if there is response HTTP header with [name] set
     */
    public operator fun contains(name: String): Boolean = get(name) != null

    /**
     * Find first response HTTP header with [name] or return `null`
     */
    public open operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()

    /**
     * Find all response HTTP header values for [name]
     */
    public fun values(name: String): List<String> = getEngineHeaderValues(name)

    /***
     * Build a [Headers] instance from response HTTP header values
     */
    public fun allValues(): Headers = Headers.build {
        getEngineHeaderNames().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    /**
     * Append HTTP response header
     * @param safeOnly `true` by default, prevents from setting unsafe headers
     */
    public fun append(name: String, value: String, safeOnly: Boolean = true) {
        if (safeOnly && HttpHeaders.isUnsafe(name)) {
            throw UnsafeHeaderException(name)
        }
        HttpHeaders.checkHeaderName(name)
        HttpHeaders.checkHeaderValue(value)
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

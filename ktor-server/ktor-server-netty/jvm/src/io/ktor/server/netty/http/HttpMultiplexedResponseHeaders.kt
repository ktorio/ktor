/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http

import io.ktor.server.response.*
import io.ktor.util.*
import io.netty.handler.codec.*

/**
 * Shared [ResponseHeaders] implementation for multiplexed HTTP protocols (HTTP/2 and HTTP/3).
 *
 * Both HTTP/2 and HTTP/3 use `DefaultHeaders<CharSequence, CharSequence, T>` for response headers.
 * This class abstracts over those headers so the same response header logic
 * can be reused regardless of the specific protocol version.
 */
internal class HttpMultiplexedResponseHeaders(
    private val underlying: Headers<CharSequence, CharSequence, *>
) : ResponseHeaders() {
    override fun engineAppendHeader(name: String, value: String) {
        underlying.add(name.toLowerCasePreservingASCIIRules(), value)
    }

    override fun get(name: String): String? = if (name.startsWith(':')) null else underlying[name]?.toString()

    override fun getEngineHeaderNames(): List<String> = underlying.names()
        .filter { !it.startsWith(':') }.map { it.toString() }

    override fun getEngineHeaderValues(name: String): List<String> =
        if (name.startsWith(':')) emptyList() else underlying.getAll(name).map { it.toString() }
}

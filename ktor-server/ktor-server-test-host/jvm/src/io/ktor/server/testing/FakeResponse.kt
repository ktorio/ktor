/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class FakeResponse(
    override val call: ApplicationCall,
    override val pipeline: ApplicationSendPipeline = ApplicationSendPipeline(true),
    override val headers: ResponseHeaders = FakeResponseHeaders(),
    private var statusCode: HttpStatusCode = HttpStatusCode.OK
) : ApplicationResponse {
    override val cookies: ResponseCookies = ResponseCookies(this, secureTransport = false)

    override fun status(): HttpStatusCode {
        return statusCode
    }

    override fun status(value: HttpStatusCode) {
        statusCode = value
    }

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder) {}
}

class FakeResponseHeaders: ResponseHeaders() {
    private val headers = mutableMapOf<String, MutableList<String>>()

    override fun engineAppendHeader(name: String, value: String) {
        if (headers.contains(name)) {
            headers[name]?.add(value)
        } else {
            headers[name] = mutableListOf(value)
        }
    }

    override fun getEngineHeaderNames(): List<String> {
        return headers.keys.toList()
    }

    override fun getEngineHeaderValues(name: String): List<String> {
        return headers.getOrDefault(name, emptyList())
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

fun TestApplicationEngine.urlShouldBeHandled(url: String, content: String? = null) {
    on("making get request to $url") {
        val result = handleRequest {
            uri = url
            method = HttpMethod.Get
        }
        it("should have a response with OK status") {
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        if (content != null) {
            it("should have a response with content '$content'") {
                assertEquals(content, result.response.content)
            }
        }
    }
}

fun TestApplicationEngine.urlShouldBeUnhandled(url: String) {
    on("making post request to $url") {
        val result = handleRequest {
            uri = url
            method = HttpMethod.Post
        }
        it("should not be handled") {
            assertFalse(result.response.status()!!.isSuccess())
        }
    }
}

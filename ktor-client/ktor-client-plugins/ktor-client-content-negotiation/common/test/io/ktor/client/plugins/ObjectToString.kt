/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*

class ObjectToString {
    companion object : HttpClientPlugin<Unit, Unit> {
        override val key: AttributeKey<Unit> = AttributeKey("ObjectToString")

        override fun install(plugin: Unit, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) {
                if (context.headers[HttpHeaders.ContentType] != ContentType.Application.Json.toString()) {
                    return@intercept
                }

                val name = it::class.simpleName ?: "null"
                proceedWith(TextContent(name, ContentType.Text.Plain))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.After) {
                if (context.response.headers[HttpHeaders.ContentType] != ContentType.Application.Json.toString()) {
                    return@intercept
                }

                proceedWith(HttpResponseContainer(typeInfo<ContentNegotiationTests.X>(), ContentNegotiationTests.X))
            }
        }

        override fun prepare(block: Unit.() -> Unit) {
        }
    }
}

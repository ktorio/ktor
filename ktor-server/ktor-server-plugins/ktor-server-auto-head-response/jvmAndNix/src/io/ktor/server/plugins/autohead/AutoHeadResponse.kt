/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.autohead

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * A plugin that automatically respond to HEAD requests.
 */
public val AutoHeadResponse: ApplicationPlugin<Application, Unit, PluginInstance> =
    createApplicationPlugin("AutoHeadResponse") {

        onCall { call ->
            if (call.request.local.method != HttpMethod.Head) return@onCall
            call.mutableOriginConnectionPoint.method = HttpMethod.Get
        }

        onCallRespond.afterTransform { call, _ ->
            if (call.request.local.method != HttpMethod.Head) return@afterTransform

            transformBody { body ->
                if (body is OutgoingContent.NoContent) return@transformBody body
                HeadResponse(body)
            }
        }
    }

private class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode? get() = original.status
    override val contentType: ContentType? get() = original.contentType
    override val contentLength: Long? get() = original.contentLength
    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
    override val headers get() = original.headers
}

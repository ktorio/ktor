/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.methodoverride

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.*

private object Setup : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Setup) {
            handler(call)
        }
    }
}

/**
 * A plugin that supports overriding HTTP method by the `X-Http-Method-Override` header.
 */
public val XHttpMethodOverride: ApplicationPlugin<Application, XHttpMethodOverrideConfig, PluginInstance> =
    createApplicationPlugin("XHttpMethodOverride", ::XHttpMethodOverrideConfig) {
        on(Setup) { call ->
            call.request.header(pluginConfig.headerName)?.takeIf { it.isNotBlank() }?.let { methodOverride ->
                call.mutableOriginConnectionPoint.method = HttpMethod.parse(methodOverride)
            }
        }
    }

/**
 * A config for [XHttpMethodOverride]
 */
@KtorDsl
public class XHttpMethodOverrideConfig {
    /**
     * A header name overriding the HTTP method.
     */
    public var headerName: String = HttpHeaders.XHttpMethodOverride
}

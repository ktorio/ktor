/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.methodoverride

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.*

/**
 * Supports overriding HTTP method by the `X-Http-Method-Override` header.
 */
public object XHttpMethodOverrideSupport :
    ApplicationPlugin<ApplicationCallPipeline, XHttpMethodOverrideSupport.Configuration, XHttpMethodOverrideSupport.Configuration> { // ktlint-disable max-line-length

    override val key: AttributeKey<Configuration> = AttributeKey("XHttpMethodOverrideSupport")

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: Configuration.() -> Unit
    ): Configuration {
        val config = Configuration()
        configure(config)

        pipeline.intercept(ApplicationCallPipeline.Setup) {
            call.request.header(config.headerName)?.takeIf { it.isNotBlank() }?.let { methodOverride ->
                call.mutableOriginConnectionPoint.method = HttpMethod.parse(methodOverride)
            }
        }

        return config
    }

    public class Configuration {
        /**
         * A header name overriding the HTTP method.
         */
        public var headerName: String = HttpHeaders.XHttpMethodOverride
    }
}

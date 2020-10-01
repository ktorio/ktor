/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*

/**
 * Support for overriding HTTP method by `X-Http-Method-Override` header.
 */
public object XHttpMethodOverrideSupport : ApplicationFeature<ApplicationCallPipeline,
        XHttpMethodOverrideSupport.Config, XHttpMethodOverrideSupport.Config> {

    override val key: AttributeKey<Config> = AttributeKey("XHttpMethodOverrideSupport")

    override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): Config {
        val config = Config()
        configure(config)
        val headerName = config.headerName

        pipeline.intercept(ApplicationCallPipeline.Features) {
            call.request.header(headerName)?.takeIf { it.isNotBlank() }?.let { methodOverride ->
                call.mutableOriginConnectionPoint.method = HttpMethod.parse(methodOverride)
            }
        }

        return config
    }

    public class Config {
        /**
         * Header name overriding HTTP method. `X-Http-Method-Override` by default.
         */
        public var headerName: String = HttpHeaders.XHttpMethodOverride
    }
}

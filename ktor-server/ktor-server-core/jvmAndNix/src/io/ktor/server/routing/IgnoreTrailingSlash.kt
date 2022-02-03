/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.server.application.*
import io.ktor.util.*

private val IgnoreTrailingSlashAttributeKey: AttributeKey<Unit> = AttributeKey("IgnoreTrailingSlashAttributeKey")

internal var ApplicationCall.ignoreTrailingSlash: Boolean
    get() = attributes.contains(IgnoreTrailingSlashAttributeKey)
    private set(value) = if (value) {
        attributes.put(IgnoreTrailingSlashAttributeKey, Unit)
    } else {
        attributes.remove(IgnoreTrailingSlashAttributeKey)
    }

/**
 * Plugin that ignores trailing slashes while resolving urls
 */
public class IgnoreTrailingSlash private constructor() {

    /**
     * Configuration for this plugin
     */
    @KtorDsl
    public class Configuration

    public companion object Plugin : ApplicationPlugin<ApplicationCallPipeline, Configuration, IgnoreTrailingSlash> {
        override val key: AttributeKey<IgnoreTrailingSlash> = AttributeKey("IgnoreTrailingSlash")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): IgnoreTrailingSlash {
            val plugin = IgnoreTrailingSlash()
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                call.ignoreTrailingSlash = true
            }
            return plugin
        }
    }
}

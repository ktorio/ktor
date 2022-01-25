/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

/**
 * Represents a hook that can be registered in [ApplicationPluginBuilder].
 */
@PluginsDslMarker
public interface Hook<HookHandler> {
    /**
     * Specifies how to install a hook in the [application].
     */
    public fun install(application: ApplicationCallPipeline, handler: HookHandler)
}

internal class HookHandler<T>(private val hook: Hook<T>, private val handler: T) {
    fun install(application: ApplicationCallPipeline) {
        hook.install(application, handler)
    }
}

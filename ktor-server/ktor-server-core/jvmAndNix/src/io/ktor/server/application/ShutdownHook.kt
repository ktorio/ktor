// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

/**
 * Represents a hook that is executed when the server is shutting down.
 */
public object Shutdown : Hook<(Application) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: (Application) -> Unit) {
        pipeline.environment!!.monitor.subscribe(ApplicationStopped) {
            handler(it)
        }
    }
}

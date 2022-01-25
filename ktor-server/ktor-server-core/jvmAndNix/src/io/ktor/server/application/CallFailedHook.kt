// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import kotlinx.coroutines.*

public object CallFailed : Hook<suspend (call: ApplicationCall, cause: Throwable) -> Unit> {

    override fun install(
        application: ApplicationCallPipeline,
        handler: suspend (call: ApplicationCall, cause: Throwable) -> Unit
    ) {
        application.intercept(ApplicationCallPipeline.Monitoring) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (cause: Throwable) {
                handler(call, cause)
            }
        }
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * Base class for implementing [ApplicationRequest]
 */
public abstract class BaseApplicationRequest(final override val call: ApplicationCall) : ApplicationRequest {
    override val pipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(
        call.application.environment.developmentMode
    ).apply {
        merge(call.application.receivePipeline)
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.request.*

/**
 * Base class for implementing [ApplicationRequest]
 */
abstract class BaseApplicationRequest(override val call: ApplicationCall) : ApplicationRequest {
    override val pipeline = ApplicationReceivePipeline().apply {
        merge(call.application.receivePipeline)
    }
}


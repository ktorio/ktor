package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.request.*

/**
 * Base class for implementing [ApplicationRequest]
 */
abstract class BaseApplicationRequest(override val call: ApplicationCall) : ApplicationRequest {
    override val pipeline = ApplicationReceivePipeline().apply {
        merge(call.application.receivePipeline)
    }
}


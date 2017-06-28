package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

/**
 * Represents an application call being handled by [Routing]
 */
open class RoutingApplicationCall(call: ApplicationCall,
                                  override val receivePipeline: ApplicationReceivePipeline,
                                  override val sendPipeline: ApplicationSendPipeline,
                                  val route: Route,
                                  private val resolvedValues: ValuesMap) : ApplicationCall by call {

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            appendAll(call.parameters)
            appendMissing(resolvedValues)
        }
    }

    override fun toString() = "RoutingApplicationCall(route=$route)"
}
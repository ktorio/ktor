package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Represents an application call being handled by [Routing]
 * @property route is the selected route
 */
class RoutingApplicationCall(private val call: ApplicationCall,
                             val route: Route,
                             receivePipeline: ApplicationReceivePipeline,
                             responsePipeline: ApplicationSendPipeline,
                             parameters: Parameters) : ApplicationCall {

    override val application: Application get() = call.application
    override val attributes: Attributes get() = call.attributes

    override val request = RoutingApplicationRequest(this, receivePipeline, call.request)
    override val response = RoutingApplicationResponse(this, responsePipeline, call.response)

    override val parameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        Parameters.build {
            appendAll(call.parameters)
            appendMissing(parameters)
        }
    }

    override fun toString() = "RoutingApplicationCall(route=$route)"
}

/**
 * Represents an application request being handled by [Routing]
 */
class RoutingApplicationRequest(override val call: RoutingApplicationCall,
                                override val pipeline: ApplicationReceivePipeline,
                                request: ApplicationRequest) : ApplicationRequest by request

/**
 * Represents an application response being handled by [Routing]
 */
class RoutingApplicationResponse(override val call: RoutingApplicationCall,
                                 override val pipeline: ApplicationSendPipeline,
                                 response: ApplicationResponse) : ApplicationResponse by response

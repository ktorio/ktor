package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*

/**
 * Feature that generates an OPTIONS method handler based on the [Routing] tree.
 *
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS
 */
object AutoOptionsResponse : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>("Automatic Options Response")

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        Unit.configure()

        pipeline.intercept(ApplicationCallPipeline.Call) {
            if (call.request.local.method == HttpMethod.Options) {
                val methods = computeAvailableMethodsForCall(call)
                call.response.header("Allow", methods.joinToString(", ") { it.value }.toUpperCase())
                call.respondText("", status = HttpStatusCode.OK)
            }
        }
    }

    fun computeAvailableMethodsForCall(call: ApplicationCall): Set<HttpMethod> {
        val otherMethods = LinkedHashSet<HttpMethod>()
        val leafMethods = LinkedHashSet<HttpMethod>()
        val routing = call.application.routing { }
        val resolveContext = RoutingResolveContext(
            routing, call, listOf(),
            shortCircuitByQuality = false
        ) { context, segmentIndex ->
            if (selector is HttpMethodRouteSelector) {
                if (children.isEmpty() && handlers.isNotEmpty()) {
                    // Leaf!
                    if (segmentIndex >= context.segments.size) {
                        leafMethods += selector.method
                    } else {
                        otherMethods += selector.method
                    }
                }

                // Accept all methods
                RouteSelectorEvaluation.Constant
            } else {
                selector.evaluate(context, segmentIndex)
            }
        }
        val result = resolveContext.resolve()
        return if (result is RoutingResolveResult.Success) {
            leafMethods.takeIf { it.isNotEmpty() } ?: otherMethods
        } else {
            setOf()
        }
    }
}

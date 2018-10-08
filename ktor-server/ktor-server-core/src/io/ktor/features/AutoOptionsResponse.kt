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
object AutoOptionsResponse : ApplicationFeature<Application, AutoOptionsResponse.Configuration, Unit> {
    override val key = AttributeKey<Unit>("Automatic Options Response")

    object Configuration

    override fun install(pipeline: Application, configure: AutoOptionsResponse.Configuration.() -> Unit) {
        configure(Configuration)

        pipeline.intercept(ApplicationCallPipeline.Call) {
            if (call.request.local.method == HttpMethod.Options) {
                val methods = computeAvailableMethodsForCall(call)
                call.response.header(HttpHeaders.Allow, methods.joinToString(", ") { it.value }.toUpperCase())
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
            val result = if (selector is HttpMethodRouteSelector) {
                // Accept all methods
                RouteSelectorEvaluation.Constant
            } else {
                selector.evaluate(context, segmentIndex)
            }

            if (result.succeeded && children.isEmpty() && handlers.isNotEmpty()) {
                val method = tryGetMethodSelectorInAncestors()
                if (method != null) {
                    val finalSegmentIndex = segmentIndex + result.segmentIncrement

                    if (finalSegmentIndex >= context.segments.size) {
                        leafMethods += method
                    } else {
                        otherMethods += method
                    }
                } else {
                }
            }

            result
        }
        val result = resolveContext.resolve()
        return if (result is RoutingResolveResult.Success) {
            leafMethods.takeIf { it.isNotEmpty() } ?: otherMethods
        } else {
            setOf()
        }
    }

    private fun Route?.tryGetMethodSelectorInAncestors(): HttpMethod? = when {
        this == null -> null
        this.selector is HttpMethodRouteSelector -> this.selector.method
        else -> this.parent.tryGetMethodSelectorInAncestors()
    }
}

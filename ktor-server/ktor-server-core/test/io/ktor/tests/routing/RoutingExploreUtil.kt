package io.ktor.tests.routing

import io.ktor.routing.*

@Suppress("unused")
fun Routing.setRoutingEvaluateHook(hook: Route.(context: RoutingResolveContext, segmentIndex: Int) -> RouteSelectorEvaluation) {
    this.evaluateHook = hook
}

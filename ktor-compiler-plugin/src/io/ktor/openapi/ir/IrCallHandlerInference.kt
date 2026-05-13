package io.ktor.openapi.ir

import io.ktor.openapi.routing.RouteField
import org.jetbrains.kotlin.ir.expressions.IrCall

/**
 * Any call with extension receivers that would likely be found in a routing handler
 * are processed for potential OpenAPI information that can be applied to the annotation
 * chained call.
 */
fun interface IrCallHandlerInference {
    companion object {
        fun of(vararg inferences: IrCallHandlerInference) =
            IrCallHandlerInference { call ->
                inferences.firstNotNullOfOrNull { it.findRouteDetails(call) }
            }
    }

    context(context: CodeGenContext)
    fun findRouteDetails(call: IrCall): List<RouteField>?
}
package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.expressions.IrCall

val RequestHeaderInference = IrCallHandlerInference { call: IrCall ->
    if (call.symbol.owner.name.asString() != "header") return@IrCallHandlerInference null
    if (!call.receiverIsType("io.ktor.server.routing.RoutingRequest")) return@IrCallHandlerInference null
    val keyExpression = call.arguments.firstOrNull() ?: return@IrCallHandlerInference null
    val keyReference = LocalReference.of(keyExpression) ?: return@IrCallHandlerInference null
    listOf(RouteField.Parameter(ParamIn.HEADER, keyReference))
}
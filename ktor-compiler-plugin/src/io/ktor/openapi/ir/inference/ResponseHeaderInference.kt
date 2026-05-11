package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.IrCallHandlerInference
import io.ktor.openapi.ir.receiverIsType
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall

val AppendResponseHeaderInference = IrCallHandlerInference { call: IrCall ->
    if (call.symbol.owner.name.asString() != "append") return@IrCallHandlerInference null
    if (!call.receiverIsType("io.ktor.server.response.ResponseHeaders")) return@IrCallHandlerInference null
    val keyParameter = call.symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return@IrCallHandlerInference null
    val keyExpression = call.arguments[keyParameter.indexInParameters] ?: return@IrCallHandlerInference null
    val keyReference = LocalReference.of(keyExpression) ?: return@IrCallHandlerInference null

    listOf(RouteField.ResponseHeader(keyReference))
}

val ResponseHeaderExtensionInference = IrCallHandlerInference { call: IrCall ->
    if (call.symbol.owner.name.asString() != "header") return@IrCallHandlerInference null
    if (!call.receiverIsType("io.ktor.server.response.ApplicationResponse")) return@IrCallHandlerInference null
    val keyParameter = call.symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return@IrCallHandlerInference null
    val keyExpression = call.arguments[keyParameter.indexInParameters] ?: return@IrCallHandlerInference null
    val keyReference = LocalReference.of(keyExpression) ?: return@IrCallHandlerInference null

    listOf(RouteField.ResponseHeader(keyReference))
}
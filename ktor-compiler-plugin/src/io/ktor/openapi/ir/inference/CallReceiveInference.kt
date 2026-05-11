package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.IrCallHandlerInference
import io.ktor.openapi.ir.isApplicationCall
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.TypeReference.Companion.asReference
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.kotlinFqName

val CallReceiveInference = IrCallHandlerInference { call: IrCall ->
    if (!call.isApplicationCall()) return@IrCallHandlerInference null

    val packageFqName = call.symbol.owner.parent.kotlinFqName.asString()
    if (packageFqName != "io.ktor.server.request") return@IrCallHandlerInference null

    val functionName = call.symbol.owner.name.asString()
    if (!functionName.startsWith("receive")) return@IrCallHandlerInference null

    val requestContentType = getContentTypeArgument(call)
    val requestBodyType = call.typeArguments.firstOrNull()?.asReference()
        ?: return@IrCallHandlerInference null

    listOf(
        RouteField.Body(
            contentType = requestContentType,
            typeReference = requestBodyType
        )
    )
}
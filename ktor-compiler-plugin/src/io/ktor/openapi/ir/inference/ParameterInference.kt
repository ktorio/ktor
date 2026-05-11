package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.TypeReference.Companion.asReference
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isString

val ParameterInference = IrCallHandlerInference { call: IrCall ->
    val functionName = call.symbol.owner.name.asString()

    when (functionName) {
        // StringValues lookup
        "get", "getAll", "getOrFail" -> {
            val keyArg = call.symbol.owner.parameters.firstOrNull {
                it.kind == IrParameterKind.Regular && it.type.isString()
            } ?: return@IrCallHandlerInference null
            val parameterName = call.arguments[keyArg.indexInParameters]
                ?.let { LocalReference.of(it) }
                ?: return@IrCallHandlerInference null
            inferFromStringValuesAccess(
                call = call,
                parameterName = parameterName,
                parameterType = null
            )
        }
        // Delegate property access
        "getValue" -> {
            val receiver = call.functionReceiver ?: return@IrCallHandlerInference null
            if (receiver.type.classFqName?.asString() != "io.ktor.http.Parameters")
                return@IrCallHandlerInference null
            val propertyReference = call.arguments.filterIsInstance<IrLocalDelegatedPropertyReference>().firstOrNull()
                ?: return@IrCallHandlerInference null
            val propertyName = propertyReference.symbol.owner.name.asString()
            inferFromStringValuesAccess(
                call = call,
                parameterName = LocalReference.of(propertyName),
                parameterType = call.type.asReference()
            )
        }

        else -> null
    }
}

private fun inferFromStringValuesAccess(
    call: IrCall,
    parameterName: LocalReference,
    parameterType: TypeReference?,
): List<RouteField>? {
    val receiver = call.functionReceiver ?: return null
    val receiverTypeName = receiver.type.classFqName?.asString()
    if (receiverTypeName !in listOf("io.ktor.util.StringValues", "io.ktor.http.Parameters")) return null

    val receiverCall = call.arguments[receiver.indexInParameters] as? IrCall
    val inValue = when (receiverCall?.symbol?.owner?.name?.asString()) {
        "<get-headers>" -> ParamIn.HEADER
        "<get-pathVariables>",
        "<get-pathParameters>" -> ParamIn.PATH
        "<get-queryParameters>" -> ParamIn.QUERY
        else -> null
    }
    return listOf(
        RouteField.Parameter(
            `in` = inValue,
            name = parameterName,
            typeReference = parameterType
        )
    )
}

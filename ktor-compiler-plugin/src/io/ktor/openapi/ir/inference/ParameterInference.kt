package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.TypeReference.Companion.asReference
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.kotlinFqName

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
        // requireXxx accessors on ApplicationCall / RoutingCall
        "requireQueryParameter",
        "requireHeader",
        "requireCookie",
        "requirePathParameter" -> inferFromRequireAccess(call, functionName)

        else -> null
    }
}

context(context: CodeGenContext)
private fun inferFromRequireAccess(
    call: IrCall,
    functionName: String,
): List<RouteField>? {
    val packageFqName = call.symbol.owner.parent.kotlinFqName.asString()
    if (packageFqName != "io.ktor.server.request") return null

    val keyArg = call.symbol.owner.parameters.firstOrNull {
        it.kind == IrParameterKind.Regular && it.type.isString()
    } ?: return null
    val parameterName = call.arguments[keyArg.indexInParameters]
        ?.let { LocalReference.of(it) }
        ?: return null

    val inValue = when (functionName) {
        "requireQueryParameter" -> ParamIn.QUERY
        "requireHeader" -> ParamIn.HEADER
        "requireCookie" -> ParamIn.COOKIE
        "requirePathParameter" -> ParamIn.PATH
        else -> return null
    }
    return listOf(
        RouteField.Parameter(
            `in` = inValue,
            name = parameterName,
        )
    )
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

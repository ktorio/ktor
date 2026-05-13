package io.ktor.openapi.ir.generators

import io.ktor.openapi.Logger
import io.ktor.openapi.ir.IrDescribeExpressionGenerator
import io.ktor.openapi.ir.assignProperty
import io.ktor.openapi.ir.callFunctionWithScope
import io.ktor.openapi.ir.unaryPlus
import io.ktor.openapi.routing.RouteField

val ParametersGenerator = IrDescribeExpressionGenerator<RouteField.Parameter> { fields ->
    if (fields.isEmpty()) return@IrDescribeExpressionGenerator

    +callFunctionWithScope("parameters") {
        for (field in fields) {
            try {
                +callFunctionWithScope(field.functionName, field.name.asExpression()) {
                    assignProperty("description", field.description)
                    field.typeReference?.let {
                        assignSchemaProperty(field.typeReference, field.schemaAttributes)
                    }
                    generateExtensionProperties(field)
                }
            } catch (e: Throwable) {
                contextOf<Logger>().log("Failed generating parameters for $field: ${e.message}")
            }
        }
    }
}

private val RouteField.Parameter.functionName get() =
    `in`?.name?.lowercase() ?: "parameter"
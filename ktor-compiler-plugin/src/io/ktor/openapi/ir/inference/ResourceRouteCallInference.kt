
package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RoutingFunctionConstants.HTTP_METHODS
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.FqName

val ResourceRouteCallInference = IrCallHandlerInference { call: IrCall ->
    if (!isResourceRouteFunction(call)) return@IrCallHandlerInference null

    // Extract the resource type from the type parameters
    val resourceType = call.typeArguments.getOrNull(0) ?: return@IrCallHandlerInference null
    val resourceClass = resourceType.classOrNull?.owner ?: return@IrCallHandlerInference null
    val fullPath = getFullResourcePath(resourceClass) ?: return@IrCallHandlerInference null

    buildList {
        // Extract path parameter placeholders from the full path
        val pathParamKeys = Regex("\\{([^}]+)}")
            .findAll(fullPath)
            .map { it.groupValues[1] }
            .toSet()

        // Add path parameters
        getPathParameters(resourceClass, pathParamKeys).forEach { paramInfo ->
            add(RouteField.Parameter(
                `in` = ParamIn.PATH,
                name = LocalReference.of(paramInfo.name),
                typeReference = paramInfo.typeReference
            ))
        }

        // Add query parameters (non-path constructor parameters)
        getQueryParameters(resourceClass, pathParamKeys).forEach { paramInfo ->
            add(RouteField.Parameter(
                `in` = ParamIn.QUERY,
                name = LocalReference.of(paramInfo.name),
                typeReference = paramInfo.typeReference
            ))
        }
    }
}

private fun isResourceRouteFunction(call: IrCall): Boolean {
    val fqName = call.symbol.owner.kotlinFqName
    val functionName = fqName.shortName().asString()
    val packageName = fqName.parent().asString()

    return packageName == "io.ktor.server.resources" &&
            functionName in HTTP_METHODS &&
            call.typeArguments.isNotEmpty()
}

/**
 * Gets the full path by traversing the resource hierarchy
 */
private fun getFullResourcePath(resourceClass: IrClass): String? {
    val paths = buildList {
        var currentClass: IrClass? = resourceClass

        while (currentClass != null) {
            // Get the @Resource annotation
            val resourceAnnotation = currentClass.getAnnotation(
                FqName("io.ktor.resources.Resource")
            ) ?: break

            // Extract the path from the annotation
            val path = resourceAnnotation.arguments.getOrNull(0)
                ?.let { arg ->
                    // The path is the first argument - extract string value
                    (arg as? IrConst)?.value as? String
                } ?: ""

            add(0, path)

            // Find parent resource type if it exists
            currentClass = findParentResourceType(currentClass)
        }
    }

    // Combine paths, handling leading/trailing slashes correctly
    return when(paths.size) {
        0 -> null
        1 -> paths.first()
        else -> paths.joinToString("") { segment ->
            if (segment.isEmpty() || segment == "/") "" else {
                if (segment.startsWith("/")) segment else "/$segment"
            }
        }
    }
}

/**
 * Finds the parent resource type if this resource has a parent field
 */
private fun findParentResourceType(resourceClass: IrClass): IrClass? {
    // Look for a constructor parameter named "parent"
    val primaryConstructor = resourceClass.primaryConstructor ?: return null
    val parentParameter = primaryConstructor.parameters.find {
        it.name.asString() == "parent"
    } ?: return null

    return parentParameter.type.classOrNull?.owner
}

/**
 * Extracts path parameters from the resource class
 */
context(context: CodeGenContext)
private fun getPathParameters(resourceClass: IrClass, pathParams: Set<String>): List<ParameterInfo> {
    val result = mutableListOf<ParameterInfo>()
    var currentClass: IrClass? = resourceClass

    while (currentClass != null) {
        val primaryConstructor = currentClass.primaryConstructor ?: break

        for (param in primaryConstructor.parameters) {
            val paramName = param.name.asString()
            if (paramName != "parent" && pathParams.contains(paramName)) {
                result.add(
                    ParameterInfo(
                        name = paramName,
                        typeReference = TypeReference.Resolved(param.type),
                        hasDefault = param.defaultValue != null
                    )
                )
            }
        }

        currentClass = findParentResourceType(currentClass)
    }

    return result
}

/**
 * Extracts query parameters from the resource class
 */
context(context: CodeGenContext)
private fun getQueryParameters(resourceClass: IrClass, pathParams: Set<String>): List<ParameterInfo> {
    val result = mutableListOf<ParameterInfo>()
    var currentClass: IrClass? = resourceClass

    while (currentClass != null) {
        val primaryConstructor = currentClass.primaryConstructor ?: break

        for (param in primaryConstructor.parameters) {
            val paramName = param.name.asString()
            if (paramName != "parent" && !pathParams.contains(paramName)) {
                result.add(
                    ParameterInfo(
                        name = paramName,
                        typeReference = TypeReference.Resolved(param.type),
                        hasDefault = param.defaultValue != null
                    )
                )
            }
        }

        currentClass = findParentResourceType(currentClass)
    }

    return result
}

/**
 * Helper class to store parameter information
 */
private data class ParameterInfo(
    val name: String,
    val typeReference: TypeReference,
    val hasDefault: Boolean
)
package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

data class RouteSelectorEvaluation(val succeeded: Boolean,
                                   val quality: Double,
                                   val values: ValuesMap = ValuesMap.Empty,
                                   val segmentIncrement: Int = 0) {
    companion object {
        val Failed = RouteSelectorEvaluation(false, 0.0)
        val Missing = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityMissing)
        val Constant = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant)

        val ConstantPath = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant, segmentIncrement = 1)
        val WildcardPath = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityWildcard, segmentIncrement = 1)

        val qualityConstant = 1.0
        val qualityParameter = 0.8
        val qualityWildcard = 0.5
        val qualityMissing = 0.2
    }
}

abstract class RouteSelector(val quality: Double) {
    abstract fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation
}

data class ConstantParameterRouteSelector(val name: String, val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.parameters.contains(name, value))
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name = $value]"
}

data class ParameterRouteSelector(val name: String) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to param))
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name]"
}

data class OptionalParameterRouteSelector(val name: String) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to param))
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "[$name?]"
}

data class UriPartConstantRouteSelector(val name: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size && context.path[index] == name)
            return RouteSelectorEvaluation.ConstantPath
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = name
}

data class UriPartParameterRouteSelector(val name: String, val prefix: String? = null, val suffix: String? = null) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size) {
            val part = context.path[index]
            val prefixChecked = if (prefix == null)
                part
            else
                if (part.startsWith(prefix))
                    part.drop(prefix.length)
                else
                    return RouteSelectorEvaluation.Failed

            val suffixChecked = if (suffix == null)
                prefixChecked
            else
                if (prefixChecked.endsWith(suffix))
                    prefixChecked.dropLast(suffix.length)
                else
                    return RouteSelectorEvaluation.Failed

            val values = valuesOf(name to listOf(suffixChecked))
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, values, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "$prefix{$name}$suffix"
}

data class UriPartOptionalParameterRouteSelector(val name: String, val prefix: String? = null, val suffix: String? = null) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size) {
            val part = context.path[index]
            val prefixChecked = if (prefix == null)
                part
            else
                if (part.startsWith(prefix))
                    part.drop(prefix.length)
                else
                    return RouteSelectorEvaluation.Missing

            val suffixChecked = if (suffix == null)
                prefixChecked
            else
                if (prefixChecked.endsWith(suffix))
                    prefixChecked.dropLast(suffix.length)
                else
                    return RouteSelectorEvaluation.Missing

            val values = valuesOf(name to listOf(suffixChecked))
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, values, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "${prefix ?: ""}{$name?}${suffix ?: ""}"
}

object UriPartWildcardRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityWildcard) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size)
            return RouteSelectorEvaluation.WildcardPath
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "*"
}

data class UriPartTailcardRouteSelector(val name: String = "") : RouteSelector(RouteSelectorEvaluation.qualityWildcard) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val values = if (name.isEmpty()) valuesOf() else valuesOf(name to context.path.drop(index).map { it })
        val quality = if (index < context.path.size) RouteSelectorEvaluation.qualityWildcard else RouteSelectorEvaluation.qualityMissing
        return RouteSelectorEvaluation(true, quality, values, segmentIncrement = context.path.size - index)
    }

    override fun toString(): String = "{...}"
}

data class OrRouteSelector(val first: RouteSelector, val second: RouteSelector) : RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, index)
        if (result.succeeded)
            return result
        else
            return second.evaluate(context, index)
    }

    override fun toString(): String = "{$first | $second}"
}

data class AndRouteSelector(val first: RouteSelector, val second: RouteSelector) : RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, index)
        if (!result1.succeeded)
            return result1
        val result2 = second.evaluate(context, index + result1.segmentIncrement)
        if (!result2.succeeded)
            return result2
        val resultValues = result1.values + result2.values
        return RouteSelectorEvaluation(true, result1.quality * result2.quality, resultValues, result1.segmentIncrement + result2.segmentIncrement)
    }

    override fun toString(): String = "{$first & $second}"
}

data class HttpMethodRouteSelector(val method: HttpMethod) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.call.request.httpMethod == method)
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(method:${method.value})"
}

data class HttpHeaderRouteSelector(val name: String, val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val headers = context.headers[name]
        val parsedHeaders = parseAndSortHeader(headers)
        val header = parsedHeaders.firstOrNull { it.value == value }
        if (header != null)
            return RouteSelectorEvaluation(true, header.quality)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(header:$name = $value)"
}

package io.ktor.routing

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*

/**
 * Represents a result of a route evaluation against a call
 *
 * @param succeeded indicates if a route matches current [RoutingResolveContext]
 * @param quality indicates quality of this route as compared to other sibling routes
 * @param parameters is an instance of [Parameters] with parameters filled by [RouteSelector]
 * @param segmentIncrement is a value indicating how many path segments has been consumed by a selector
 */
data class RouteSelectorEvaluation(val succeeded: Boolean,
                                   val quality: Double,
                                   val parameters: Parameters = Parameters.Empty,
                                   val segmentIncrement: Int = 0) {
    companion object {
        /**
         * Quality of [RouteSelectorEvaluation] when a constant value has matched
         */
        const val qualityConstant = 1.0

        /**
         * Quality of [RouteSelectorEvaluation] when a parameter has matched
         */
        const val qualityParameter = 0.8

        /**
         * Quality of [RouteSelectorEvaluation] when a wildcard has matched
         */
        const val qualityWildcard = 0.5

        /**
         * Quality of [RouteSelectorEvaluation] when an optional parameter was missing
         */
        const val qualityMissing = 0.2

        /**
         * Quality of [RouteSelectorEvaluation] when a tailcard match has occurred
         */
        const val qualityTailcard = 0.1

        /**
         * Route evaluation failed to succeed, route doesn't match a context
         */
        val Failed = RouteSelectorEvaluation(false, 0.0)

        /**
         * Route evaluation succeeded for a missing optional value
         */
        val Missing = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityMissing)

        /**
         * Route evaluation succeeded for a constant value
         */
        val Constant = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant)

        /**
         * Route evaluation succeeded for a single path segment with a constant value
         */
        val ConstantPath = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant, segmentIncrement = 1)

        /**
         * Route evaluation succeeded for a wildcard path segment
         */
        val WildcardPath = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityWildcard, segmentIncrement = 1)
    }
}

/**
 * Base type for all routing selectors
 *
 * @param quality indicates how good this selector is compared to siblings
 */
abstract class RouteSelector(val quality: Double) {
    /**
     * Evaluates this selector against [context] and a path segment at [segmentIndex]
     */
    abstract fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation
}

/**
 * The selector for routing root.
 */
@InternalAPI
class RootRouteSelector(rootPath: String = "") : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    private val parts = RoutingPath.parse(rootPath).parts.map {
        require(it.kind == RoutingPathSegmentKind.Constant) {
            "rootPath should be constant, no wildcards supported."
        }
        it.value
    }
    private val successEvaluationResult = RouteSelectorEvaluation(
        true, RouteSelectorEvaluation.qualityConstant,
        segmentIncrement = parts.size
    )

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        check(segmentIndex == 0) { "Root selector should be evaluated first." }
        if (parts.isEmpty()) {
            return RouteSelectorEvaluation.Constant
        }

        val parts = parts
        val segments = context.segments
        if (segments.size < parts.size) {
            return RouteSelectorEvaluation.Failed
        }

        for (index in segmentIndex until segmentIndex + parts.size) {
            if (segments[index] != parts[index]) {
                return RouteSelectorEvaluation.Failed
            }
        }

        return successEvaluationResult
    }

    override fun toString(): String = parts.joinToString("/")
}

/**
 * Evaluates a route against a constant query parameter value
 * @param name is a name of the query parameter
 * @param value is a value of the query parameter
 */
data class ConstantParameterRouteSelector(val name: String, val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.parameters.contains(name, value))
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name = $value]"
}

/**
 * Evaluates a route against a query parameter value and captures its value
 * @param name is a name of the query parameter
 */
data class ParameterRouteSelector(val name: String) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, parametersOf(name, param))
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name]"
}

/**
 * Evaluates a route against an optional query parameter value and captures its value, if found
 * @param name is a name of the query parameter
 */
data class OptionalParameterRouteSelector(val name: String) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, parametersOf(name, param))
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "[$name?]"
}

/**
 * Evaluates a route against a constant path segment
 * @param value is a value of the path segment
 */
data class PathSegmentConstantRouteSelector(val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size && context.segments[segmentIndex] == value)
            return RouteSelectorEvaluation.ConstantPath
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = value
}

/**
 * Evaluates a route against a parameter path segment and captures its value
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
data class PathSegmentParameterRouteSelector(val name: String, val prefix: String? = null, val suffix: String? = null) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size) {
            val part = context.segments[segmentIndex]
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

            val values = parametersOf(name, suffixChecked)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, values, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "${prefix ?: ""}{$name}${suffix ?: ""}"
}

/**
 * Evaluates a route against an optional parameter path segment and captures its value, if any
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
data class PathSegmentOptionalParameterRouteSelector(val name: String, val prefix: String? = null, val suffix: String? = null) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size) {
            val part = context.segments[segmentIndex]
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

            val values = parametersOf(name, suffixChecked)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, values, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "${prefix ?: ""}{$name?}${suffix ?: ""}"
}

/**
 * Evaluates a route against any single path segment
 */
object PathSegmentWildcardRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityWildcard) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size)
            return RouteSelectorEvaluation.WildcardPath
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "*"
}

/**
 * Evaluates a route against any number of trailing path segments, and captures their values
 * @param name is the name of the parameter to capture values to
 */
data class PathSegmentTailcardRouteSelector(val name: String = "", val prefix: String = "") : RouteSelector(RouteSelectorEvaluation.qualityTailcard) {
    init {
        require(prefix.none { it == '/' }) { "Multisegment prefix is not supported"}
    }
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (prefix.isNotEmpty()) {
            val segmentText = context.segments.getOrNull(segmentIndex)
            if (segmentText == null || !segmentText.startsWith(prefix)) {
                return RouteSelectorEvaluation.Failed
            }
        }

        val values = when {
            name.isEmpty() -> parametersOf()
            else -> parametersOf(name, context.segments.drop(segmentIndex).mapIndexed { index, segment ->
                if (index == 0) segment.drop(prefix.length)
                else segment
            })
        }
        val quality = when {
            segmentIndex < context.segments.size -> RouteSelectorEvaluation.qualityTailcard
            else -> RouteSelectorEvaluation.qualityMissing
        }
        return RouteSelectorEvaluation(
            true, quality, values,
            segmentIncrement = context.segments.size - segmentIndex
        )
    }

    override fun toString(): String = "{...}"
}

/**
 * Evaluates a route as a result of the OR operation using two other selectors
 * @param first is a first selector
 * @param second is a second selector
 */
data class OrRouteSelector(val first: RouteSelector, val second: RouteSelector) : RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, segmentIndex)
        if (result.succeeded)
            return result
        else
            return second.evaluate(context, segmentIndex)
    }

    override fun toString(): String = "{$first | $second}"
}

/**
 * Evaluates a route as a result of the AND operation using two other selectors
 * @param first is a first selector
 * @param second is a second selector
 */
data class AndRouteSelector(val first: RouteSelector, val second: RouteSelector) : RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, segmentIndex)
        if (!result1.succeeded)
            return result1
        val result2 = second.evaluate(context, segmentIndex + result1.segmentIncrement)
        if (!result2.succeeded)
            return result2
        val resultValues = result1.parameters + result2.parameters
        return RouteSelectorEvaluation(true, result1.quality * result2.quality, resultValues, result1.segmentIncrement + result2.segmentIncrement)
    }

    override fun toString(): String = "{$first & $second}"
}

/**
 * Evaluates a route against an [HttpMethod]
 * @param method is an instance of [HttpMethod]
 */
data class HttpMethodRouteSelector(val method: HttpMethod) : RouteSelector(RouteSelectorEvaluation.qualityParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.request.httpMethod == method)
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(method:${method.value})"
}

/**
 * Evaluates a route against a header in the request
 * @param name is a name of the header
 * @param value is a value of the header
 */
data class HttpHeaderRouteSelector(val name: String, val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val headers = context.call.request.headers[name]
        val parsedHeaders = parseAndSortHeader(headers)
        val header = parsedHeaders.firstOrNull { it.value.equals(value, ignoreCase = true) }
        if (header != null)
            return RouteSelectorEvaluation(true, header.quality)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(header:$name = $value)"
}

/**
 * Evaluates a route against a content-type in the [HttpHeaders.Accept] header in the request
 * @param contentType is an instance of [ContentType]
 */
data class HttpAcceptRouteSelector(val contentType: ContentType) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val headers = context.call.request.headers["Accept"]
        val parsedHeaders = parseAndSortContentTypeHeader(headers)
        if (parsedHeaders.isEmpty())
            return RouteSelectorEvaluation.Missing
        val header = parsedHeaders.firstOrNull { contentType.match(it.value) }
        if (header != null)
            return RouteSelectorEvaluation(true, header.quality)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(contentType:$contentType)"
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * A result of a route evaluation against a call.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation)
 *
 * @param succeeded indicates if a route matches the current [RoutingResolveContext]
 */
@Suppress("RemoveRedundantQualifierName", "PublicApiImplicitType")
public sealed class RouteSelectorEvaluation(
    public val succeeded: Boolean
) {
    /**
     * A success result of a route evaluation against a call.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Success)
     *
     * @param quality indicates a quality of this route as compared to other sibling routes
     * @param parameters is an instance of [Parameters] with parameters filled by [RouteSelector]
     * @param segmentIncrement is a value indicating how many path segments has been consumed by a selector
     */
    public data class Success(
        public val quality: Double,
        public val parameters: Parameters = Parameters.Empty,
        public val segmentIncrement: Int = 0
    ) : RouteSelectorEvaluation(true)

    /**
     * A failed result of a route evaluation against a call.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Failure)
     *
     * @param quality indicates a quality of this route as compared to other sibling routes
     * @param failureStatusCode response status code in case of failure.
     * Usually one of 400, 404, 405. Ignored on successful evaluation
     */
    public data class Failure(
        public val quality: Double,
        public val failureStatusCode: HttpStatusCode
    ) : RouteSelectorEvaluation(false)

    public companion object {
        @Deprecated(
            "Please use RouteSelectorEvaluation.Failure() or RouteSelectorEvaluation.Success() constructors",
            level = DeprecationLevel.ERROR
        )
        public operator fun invoke(
            succeeded: Boolean,
            quality: Double,
            parameters: Parameters = Parameters.Empty,
            segmentIncrement: Int = 0
        ): RouteSelectorEvaluation = when (succeeded) {
            true -> RouteSelectorEvaluation.Success(quality, parameters, segmentIncrement)
            else -> RouteSelectorEvaluation.Failure(quality, HttpStatusCode.NotFound)
        }

        /**
         * Quality of [RouteSelectorEvaluation] when a constant value is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityConstant)
         */
        public const val qualityConstant: Double = 1.0

        /**
         * Quality of [RouteSelectorEvaluation] when a query parameter is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityQueryParameter)
         */
        public const val qualityQueryParameter: Double = 1.0

        /**
         * Quality of [RouteSelectorEvaluation] when a parameter with prefix or suffix is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityParameterWithPrefixOrSuffix)
         */
        public const val qualityParameterWithPrefixOrSuffix: Double = 0.9

        /**
         * Generic quality of [RouteSelectorEvaluation] to use as reference when some specific parameter is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityParameter)
         */
        public const val qualityParameter: Double = 0.8

        /**
         * Quality of [RouteSelectorEvaluation] when a path parameter is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityPathParameter)
         */
        public const val qualityPathParameter: Double = qualityParameter

        /**
         * Quality of [RouteSelectorEvaluation] when a HTTP method parameter is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityMethodParameter)
         */
        @Suppress("unused")
        public const val qualityMethodParameter: Double = qualityParameter

        /**
         * Quality of [RouteSelectorEvaluation] when a wildcard is matched.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityWildcard)
         */
        public const val qualityWildcard: Double = 0.5

        /**
         * Quality of [RouteSelectorEvaluation] when an optional parameter is missing.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityMissing)
         */
        public const val qualityMissing: Double = 0.2

        /**
         * Quality of [RouteSelectorEvaluation] when a tailcard match is occurred.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityTailcard)
         */
        public const val qualityTailcard: Double = 0.1

        /**
         * Quality of [RouteSelectorEvaluation] that doesn't have its own quality but uses quality of its children.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityTransparent)
         */
        public const val qualityTransparent: Double = -1.0

        /**
         * Quality of [RouteSelectorEvaluation] when an HTTP method doesn't match.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityFailedMethod)
         */
        public const val qualityFailedMethod: Double = 0.02

        /**
         * Quality of [RouteSelectorEvaluation] when parameter (query, header, etc) doesn't match.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.qualityFailedParameter)
         */
        public const val qualityFailedParameter: Double = 0.01

        /**
         * Routing evaluation failed to succeed, route doesn't match a context.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.Failed)
         */
        public val Failed: RouteSelectorEvaluation.Failure =
            RouteSelectorEvaluation.Failure(0.0, HttpStatusCode.NotFound)

        /**
         * Routing evaluation failed to succeed on a path selector.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.FailedPath)
         */
        public val FailedPath: RouteSelectorEvaluation.Failure =
            RouteSelectorEvaluation.Failure(0.0, HttpStatusCode.NotFound)

        /**
         * Routing evaluation failed to succeed on a method selector.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.FailedMethod)
         */
        public val FailedMethod: RouteSelectorEvaluation.Failure =
            RouteSelectorEvaluation.Failure(qualityFailedMethod, HttpStatusCode.MethodNotAllowed)

        /**
         * Routing evaluation failed to succeed on a query, header, or other parameter selector.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.FailedParameter)
         */
        public val FailedParameter: RouteSelectorEvaluation.Failure =
            RouteSelectorEvaluation.Failure(qualityFailedParameter, HttpStatusCode.BadRequest)

        /**
         * Routing evaluation succeeded for a missing optional value.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.Missing)
         */
        public val Missing: RouteSelectorEvaluation =
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityMissing)

        /**
         * Routing evaluation succeeded for a constant value.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.Constant)
         */
        public val Constant: RouteSelectorEvaluation =
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityConstant)

        /**
         * Routing evaluation succeeded for a [qualityTransparent] value. Useful for helper DSL methods that may wrap
         * routes but should not change priority of routing.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.Transparent)
         */
        public val Transparent: RouteSelectorEvaluation =
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityTransparent)

        /**
         * Routing evaluation succeeded for a single path segment with a constant value.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.ConstantPath)
         */
        public val ConstantPath: RouteSelectorEvaluation =
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityConstant, segmentIncrement = 1)

        /**
         * Routing evaluation succeeded for a wildcard path segment.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelectorEvaluation.Companion.WildcardPath)
         */
        public val WildcardPath: RouteSelectorEvaluation =
            RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityWildcard, segmentIncrement = 1)
    }
}

/**
 * Serves as the base type for routing selectors.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelector)
 *
 * @param quality indicates how good this selector is compared to siblings
 */
public abstract class RouteSelector {

    /**
     * Evaluates this selector against [context] and a path segment at [segmentIndex].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RouteSelector.evaluate)
     */
    public abstract suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation
}

/**
 * A selector for a routing root.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RootRouteSelector)
 */
public class RootRouteSelector(rootPath: String = "") : RouteSelector() {

    private val parts = RoutingPath.parse(rootPath).parts.map {
        require(it.kind == RoutingPathSegmentKind.Constant) {
            "rootPath should be constant, no wildcards supported."
        }
        it.value
    }

    private val successEvaluationResult = RouteSelectorEvaluation.Success(
        RouteSelectorEvaluation.qualityConstant,
        segmentIncrement = parts.size
    )

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        check(segmentIndex == 0) { "Root selector should be evaluated first." }
        if (parts.isEmpty()) {
            return RouteSelectorEvaluation.Constant
        }

        val parts = parts
        val segments = context.segments
        if (segments.size < parts.size) {
            return RouteSelectorEvaluation.FailedPath
        }

        for (index in segmentIndex until segmentIndex + parts.size) {
            if (segments[index] != parts[index]) {
                return RouteSelectorEvaluation.FailedPath
            }
        }

        return successEvaluationResult
    }

    override fun toString(): String = parts.joinToString("/")
}

/**
 * Evaluates a route against a constant query parameter value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.ConstantParameterRouteSelector)
 *
 * @param name is a name of the query parameter
 * @param value is a value of the query parameter
 */
public data class ConstantParameterRouteSelector(
    val name: String,
    val value: String
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.parameters.contains(name, value)) {
            return RouteSelectorEvaluation.Constant
        }
        return RouteSelectorEvaluation.FailedParameter
    }

    override fun toString(): String = "[$name = $value]"
}

/**
 * Evaluates a route against a query parameter value and captures its value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.ParameterRouteSelector)
 *
 * @param name is a name of the query parameter
 */
public data class ParameterRouteSelector(
    val name: String
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null) {
            return RouteSelectorEvaluation.Success(
                RouteSelectorEvaluation.qualityQueryParameter,
                parametersOf(name, param)
            )
        }
        return RouteSelectorEvaluation.FailedParameter
    }

    override fun toString(): String = "[$name]"
}

/**
 * Evaluates a route against an optional query parameter value and captures its value, if found.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.OptionalParameterRouteSelector)
 *
 * @param name is a name of the query parameter
 */
public data class OptionalParameterRouteSelector(
    val name: String
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null) {
            return RouteSelectorEvaluation.Success(
                RouteSelectorEvaluation.qualityQueryParameter,
                parametersOf(name, param)
            )
        }
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "[$name?]"
}

/**
 * Evaluates a route against a constant path segment.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentConstantRouteSelector)
 *
 * @param value is a value of the path segment
 */
public data class PathSegmentConstantRouteSelector(
    val value: String
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation = when {
        segmentIndex < context.segments.size && context.segments[segmentIndex] == value ->
            RouteSelectorEvaluation.ConstantPath

        else -> RouteSelectorEvaluation.FailedPath
    }

    override fun toString(): String = value
}

/**
 * Evaluates a route against a single trailing slash.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.TrailingSlashRouteSelector)
 */
public object TrailingSlashRouteSelector : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation = when {
        context.call.ignoreTrailingSlash -> RouteSelectorEvaluation.Transparent
        context.segments.isEmpty() -> RouteSelectorEvaluation.Constant
        segmentIndex < context.segments.lastIndex -> RouteSelectorEvaluation.Transparent
        segmentIndex > context.segments.lastIndex -> RouteSelectorEvaluation.FailedPath
        context.segments[segmentIndex].isNotEmpty() -> RouteSelectorEvaluation.Transparent
        context.hasTrailingSlash -> RouteSelectorEvaluation.ConstantPath
        else -> RouteSelectorEvaluation.FailedPath
    }

    override fun toString(): String = "<slash>"
}

/**
 * Evaluates a route against a parameter path segment and captures its value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentParameterRouteSelector)
 *
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
public data class PathSegmentParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return evaluatePathSegmentParameter(
            segments = context.segments,
            segmentIndex = segmentIndex,
            name = name,
            prefix = prefix,
            suffix = suffix,
            isOptional = false
        )
    }

    override fun toString(): String = "${prefix ?: ""}{$name}${suffix ?: ""}"
}

/**
 * Evaluates a route against an optional parameter path segment and captures its value, if any.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector)
 *
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
public data class PathSegmentOptionalParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return evaluatePathSegmentParameter(
            segments = context.segments,
            segmentIndex = segmentIndex,
            name = name,
            prefix = prefix,
            suffix = suffix,
            isOptional = true
        )
    }

    override fun toString(): String = "${prefix ?: ""}{$name?}${suffix ?: ""}"
}

/**
 * Evaluates a route against any single path segment.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentWildcardRouteSelector)
 */
public object PathSegmentWildcardRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size && context.segments[segmentIndex].isNotEmpty()) {
            return RouteSelectorEvaluation.WildcardPath
        }
        return RouteSelectorEvaluation.FailedPath
    }

    override fun toString(): String = "*"
}

/**
 * Evaluates a route against any number of trailing path segments, and captures their values.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentTailcardRouteSelector)
 *
 * @param name is the name of the parameter to capture values to
 * @property prefix before the tailcard (static text)
 */
public data class PathSegmentTailcardRouteSelector(
    val name: String = "",
    val prefix: String = ""
) : RouteSelector() {

    init {
        require(prefix.none { it == '/' }) { "Multisegment prefix is not supported" }
    }

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val segments = context.segments
        if (prefix.isNotEmpty()) {
            val segmentText = segments.getOrNull(segmentIndex)
            if (segmentText == null || !segmentText.startsWith(prefix)) {
                return RouteSelectorEvaluation.FailedPath
            }
        }

        val values = when {
            name.isEmpty() -> parametersOf()
            else -> parametersOf(
                name,
                segments.drop(segmentIndex).mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.drop(prefix.length)
                    } else {
                        segment
                    }
                }
            )
        }
        val quality = when {
            segmentIndex < segments.size -> RouteSelectorEvaluation.qualityTailcard
            else -> RouteSelectorEvaluation.qualityMissing
        }
        return RouteSelectorEvaluation.Success(
            quality,
            values,
            segmentIncrement = segments.size - segmentIndex
        )
    }

    override fun toString(): String = "{...}"
}

/**
 * Evaluates a route as a result of the OR operation using two other selectors.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.OrRouteSelector)
 *
 * @param first is a first selector
 * @param second is a second selector
 */
@Suppress("unused")
public data class OrRouteSelector(
    val first: RouteSelector,
    val second: RouteSelector
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, segmentIndex)
        return if (result.succeeded) {
            result
        } else {
            second.evaluate(context, segmentIndex)
        }
    }

    override fun toString(): String = "{$first | $second}"
}

/**
 * Evaluates a route as a result of the AND operation using two other selectors.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.AndRouteSelector)
 *
 * @param first is a first selector
 * @param second is a second selector
 */
@Suppress("unused")
public data class AndRouteSelector(
    val first: RouteSelector,
    val second: RouteSelector
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, segmentIndex)
        if (result1 !is RouteSelectorEvaluation.Success) {
            return result1
        }
        val result2 = second.evaluate(context, segmentIndex + result1.segmentIncrement)
        if (result2 !is RouteSelectorEvaluation.Success) {
            return result2
        }
        val resultValues = result1.parameters + result2.parameters
        return RouteSelectorEvaluation.Success(
            result1.quality * result2.quality,
            resultValues,
            result1.segmentIncrement + result2.segmentIncrement
        )
    }

    override fun toString(): String = "{$first & $second}"
}

/**
 * Evaluates a route against an [HttpMethod].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.HttpMethodRouteSelector)
 *
 * @param method is an instance of [HttpMethod]
 */
public data class HttpMethodRouteSelector(
    val method: HttpMethod
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.request.httpMethod == method) {
            return RouteSelectorEvaluation.Constant
        }
        return RouteSelectorEvaluation.FailedMethod
    }

    override fun toString(): String = "(method:${method.value})"
}

/**
 * Evaluates a route against a header in the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.HttpHeaderRouteSelector)
 *
 * @param name is the name of the header
 * @param value is the value of the header
 */
public data class HttpHeaderRouteSelector(
    val name: String,
    val value: String
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val headers = context.call.request.headers[name]
        val parsedHeaders = parseAndSortHeader(headers)
        val header = parsedHeaders.firstOrNull { it.value.equals(value, ignoreCase = true) }
            ?: return RouteSelectorEvaluation.FailedParameter

        return RouteSelectorEvaluation.Success(header.quality)
    }

    override fun toString(): String = "(header:$name = $value)"
}

/**
 * Evaluates a route against a `Content-Type` in the [HttpHeaders.ContentType] request header.
 * @param contentType is an instance of [ContentType]
 */
internal data class ContentTypeHeaderRouteSelector(
    val contentType: ContentType
) : RouteSelector() {

    private val failedEvaluation = RouteSelectorEvaluation.Failure(
        RouteSelectorEvaluation.qualityFailedParameter,
        HttpStatusCode.UnsupportedMediaType
    )

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val headers = context.call.request.header(HttpHeaders.ContentType)
        val parsedHeaders = parseAndSortContentTypeHeader(headers)

        val header = parsedHeaders.firstOrNull { ContentType.parse(it.value).match(contentType) }
            ?: return failedEvaluation

        return RouteSelectorEvaluation.Success(header.quality)
    }

    override fun toString(): String = "(contentType = $contentType)"
}

/**
 * Evaluates a route against a `Content-Type` in the [HttpHeaders.Accept] request header.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.HttpAcceptRouteSelector)
 *
 * @param contentType is an instance of [ContentType]
 */
public data class HttpAcceptRouteSelector(
    val contentType: ContentType
) : RouteSelector() {

    private val delegate = HttpMultiAcceptRouteSelector(listOf(contentType))

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return delegate.evaluate(context, segmentIndex)
    }

    override fun toString(): String = "(contentType:$contentType)"
}

/**
 * Evaluates a route against a `Content-Type` in the [HttpHeaders.Accept] request header.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.HttpMultiAcceptRouteSelector)
 *
 * @param contentTypes a list of [ContentType] to accept
 */
public data class HttpMultiAcceptRouteSelector(
    val contentTypes: List<ContentType>
) : RouteSelector() {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val acceptHeaderContent = context.call.request.headers[HttpHeaders.Accept]
        try {
            val parsedHeaders = parseAndSortContentTypeHeader(acceptHeaderContent)

            if (parsedHeaders.isEmpty()) {
                return RouteSelectorEvaluation.Missing
            }

            val header = parsedHeaders.firstOrNull { header ->
                contentTypes.any { it.isCompatibleWith(ContentType.parse(header.value)) }
            }
            if (header != null) {
                return RouteSelectorEvaluation.Success(header.quality)
            }

            return RouteSelectorEvaluation.FailedParameter
        } catch (failedToParse: BadContentTypeFormatException) {
            throw BadRequestException("Illegal Accept header format: $acceptHeaderContent", failedToParse)
        }
    }

    override fun toString(): String = "(contentTypes:$contentTypes)"
}

internal fun evaluatePathSegmentParameter(
    segments: List<String>,
    segmentIndex: Int,
    name: String,
    prefix: String? = null,
    suffix: String? = null,
    isOptional: Boolean
): RouteSelectorEvaluation {
    fun failedEvaluation(failedPart: String?): RouteSelectorEvaluation {
        return when {
            !isOptional -> RouteSelectorEvaluation.FailedPath
            failedPart == null -> RouteSelectorEvaluation.Missing
            failedPart.isEmpty() -> // trailing slash
                RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityMissing, segmentIncrement = 1)

            else -> RouteSelectorEvaluation.Missing
        }
    }

    if (segmentIndex >= segments.size) {
        return failedEvaluation(null)
    }

    val part = segments[segmentIndex]
    if (part.isEmpty()) return failedEvaluation(part)

    val prefixChecked = when {
        prefix == null -> part
        part.startsWith(prefix) -> part.drop(prefix.length)
        else -> return failedEvaluation(part)
    }

    val suffixChecked = when {
        suffix == null -> prefixChecked
        prefixChecked.endsWith(suffix) -> prefixChecked.dropLast(suffix.length)
        else -> return failedEvaluation(part)
    }

    val values = parametersOf(name, suffixChecked)
    return RouteSelectorEvaluation.Success(
        quality = when {
            prefix.isNullOrEmpty() && suffix.isNullOrEmpty() -> RouteSelectorEvaluation.qualityPathParameter
            else -> RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix
        },
        parameters = values,
        segmentIncrement = 1
    )
}

private fun ContentType.isCompatibleWith(other: ContentType): Boolean = when {
    this.contentType == "*" && this.contentSubtype == "*" -> true
    other.contentType == "*" && other.contentSubtype == "*" -> true
    this.contentSubtype == "*" -> other.match(this)
    else -> this.match(other)
}

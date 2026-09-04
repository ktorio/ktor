/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.routing.RoutingResolveResult.*
import io.ktor.utils.io.InternalAPI

/**
 * Fast-path index over the routing tree, keyed by constant path segments.
 *
 * Short-circuits routing resolution for static endpoints (e.g. `get("/hello") { ... }`)
 * where the entire route consists of constant path segments terminating in an
 * [HttpMethodRouteSelector] or a leaf with handlers.
 *
 * [tryResolve] returns a [RoutingResolveResult.Success] when the request resolves
 * unambiguously, or `null` when the caller must fall back to the regular DFS resolver.
 */
internal class RoutingPathTree private constructor(private val root: Node) {

    private sealed interface Node {
        companion object {
            operator fun invoke(
                successResult: Success?,
                routingNode: RoutingNode?,
                children: Map<String, Node>,
                methodMatches: Map<HttpMethod, Success>,
                parameter: Pair<String, Node>?,
                wildcard: Node?,
                tailcard: Pair<String, Node>?,
            ): Node = when {
                parameter != null -> ParameterNode(children, routingNode, successResult, methodMatches, parameter)
                wildcard != null -> WildcardNode(children, routingNode, successResult, methodMatches, wildcard)
                tailcard != null -> TailcardNode(children, routingNode, successResult, methodMatches, tailcard)
                else -> ElementNode(children, routingNode, successResult, methodMatches)
            }
        }
    }

    private data object AmbiguousNode : Node

    private open class ElementNode(
        val children: Map<String, Node>,
        val route: RoutingNode?,
        val success: Success?,
        val methods: Map<HttpMethod, Success>,
    ) : Node {
        fun terminalResultFor(method: HttpMethod): Success? {
            methods[method]?.let { return it }
            val node = route
            if (node != null && node.handlers.isNotEmpty() && methods.isEmpty()) {
                return success
            }
            return null
        }
    }

    private class ParameterNode(
        children: Map<String, Node>,
        route: RoutingNode?,
        success: Success?,
        methods: Map<HttpMethod, Success>,
        val parameter: Pair<String, Node>,
    ) : ElementNode(children, route, success, methods)

    private class WildcardNode(
        children: Map<String, Node>,
        route: RoutingNode?,
        success: Success?,
        methods: Map<HttpMethod, Success>,
        val wildcard: Node,
    ) : ElementNode(children, route, success, methods)

    private class TailcardNode(
        children: Map<String, Node>,
        route: RoutingNode?,
        success: Success?,
        methods: Map<HttpMethod, Success>,
        val tailcard: Pair<String, Node>,
    ) : ElementNode(children, route, success, methods)

    /**
     * Fast lookup operating directly on the raw request [path] — without allocating [RoutingResolveContext].
     *
     * Returns a cached [RoutingResolveResult.Success] when every segment matches a child
     * unambiguously and a terminal exists for [method]. Bails out (returns `null`) for
     * any ambiguous nodes.
     */
    fun tryResolve(path: String, method: HttpMethod): Success? {
        if (root !is ElementNode) return null
        val length = path.length

        // Empty or root-only path — resolve directly at the root node.
        if (length == 0 || (length == 1 && path[0] == '/')) {
            val terminal = root.terminalResultFor(method) ?: return null
            return terminal
        }

        // A trailing slash's meaning is decided per-call by [IgnoreTrailingSlash], which this
        // statically-built index cannot observe — defer such requests to the DFS resolver.
        if (path[length - 1] == '/') return null

        var parameters: ParametersBuilder? = null
        var current: Node = root
        var i = if (path[0] == '/') 1 else 0

        val addParameter: (key: String, value: String) -> Unit = { key, value ->
            if (parameters == null) parameters = ParametersBuilder()
            parameters.append(key, value)
        }

        while (i < length) {
            if (current !is ElementNode) return null
            var j = i
            var hasPercent = false
            while (j < length) {
                val c = path[j]
                if (c == '/') break
                if (c == '%') hasPercent = true
                j++
            }
            if (j == i) { // empty segment ("//"), skip
                i = j + 1
                continue
            }
            val segment = if (i == 0 && j == length) path else path.substring(i, j)
            val constantNext = current.children[segment]
            current = constantNext ?: when (current) {
                is ParameterNode -> {
                    val value = decodeSegment(segment, hasPercent)
                    // Non-optional path parameters: reject empty segments.
                    if (value.isEmpty()) return null
                    val (name, node) = current.parameter
                    addParameter(name, value)
                    node
                }

                is TailcardNode -> {
                    val (name, node) = current.tailcard
                    if (name.isNotEmpty()) {
                        captureTailcardSegments(addParameter, name, path, i, length)
                    }
                    current = node
                    break
                }

                is WildcardNode -> current.wildcard

                else -> return null
            }
            i = j + 1
        }

        if (current !is ElementNode) return null
        val terminal = current.terminalResultFor(method) ?: return null
        if (parameters == null) return terminal

        return Success(
            route = terminal.route,
            parameters = parameters.build(),
            quality = RouteSelectorEvaluation.qualityPathParameter,
        )
    }

    /**
     * Appends each segment in `path[start, end)` to [parameters] under [name], matching
     * [PathSegmentTailcardRouteSelector] capture semantics: empty segments (from `//`) are
     * skipped and each part is URL-decoded (only paying the decode cost when a `%` is present).
     */
    private fun captureTailcardSegments(
        addParameter: (String, String) -> Unit,
        name: String,
        path: String,
        start: Int,
        end: Int,
    ) {
        var segStart = start
        while (segStart < end) {
            var segEnd = segStart
            var hasPercent = false
            while (segEnd < end) {
                val c = path[segEnd]
                if (c == '/') break
                if (c == '%') hasPercent = true
                segEnd++
            }
            if (segEnd != segStart) { // skip empty segments produced by consecutive '/'
                addParameter(name, decodeSegment(path.substring(segStart, segEnd), hasPercent))
            }
            segStart = segEnd + 1
        }
    }

    /**
     * URL-decodes [segment], only paying the decode cost when [hasPercent] is set, and falling
     * back to the raw value if decoding fails.
     */
    private fun decodeSegment(segment: String, hasPercent: Boolean): String = try {
        if (hasPercent) segment.decodeURLPart() else segment
    } catch (_: Exception) {
        segment
    }

    companion object {
        /**
         * Builds a [RoutingPathTree] from a routing [root], covering only the subset
         * safe for fast-path resolution.
         */
        fun build(root: RoutingNode): RoutingPathTree =
            RoutingPathTree(
                when (val selector = root.selector) {
                    is RootRouteSelector -> buildRootNode(root, selector.rootParts)
                    else -> AmbiguousNode
                }
            )

        private fun buildRootNode(root: RoutingNode, parts: List<String>): Node {
            var currentRoot = root
            for (part in parts) {
                currentRoot = currentRoot.children.firstOrNull {
                    (it.selector as? PathSegmentConstantRouteSelector)?.value?.encodeURLPathPart() == part
                } ?: return AmbiguousNode
            }

            return registerChildren(currentRoot)
        }

        private fun makeFastPathSuccess(node: RoutingNode): Success =
            Success(
                route = node,
                parameters = Parameters.Empty,
                quality = RouteSelectorEvaluation.qualityConstant,
            )

        /**
         * Classifies children of [routingNode] into three buckets:
         *  - **Indexable**: constant path, plain parameter, method leaf, trailing slash.
         *  - **Fallback**: lower-quality siblings (wildcards, tailcards, prefix/suffix params).
         *  - **Ambiguous**: anything that could equal or outrank a constant match.
         */
        private fun registerChildren(routingNode: RoutingNode): Node {
            val routeChildren = routingNode.children
            val nodeBuilder = NodeBuilder(routingNode)

            // Multiple plain parameter siblings make scoring non-trivial — bail out.
            val parameterCount = routeChildren.count { isPlainParameterChild(it) }
            if (parameterCount > 1) return AmbiguousNode

            for (child in routeChildren) {
                if (!processChild(child, nodeBuilder)) return AmbiguousNode
            }

            // Dynamic segments are taken in order: parameter, wildcard, tailcard
            return nodeBuilder.build()
        }

        private class NodeBuilder(val route: RoutingNode) {
            val success = if (route.handlers.isNotEmpty()) makeFastPathSuccess(route) else null

            val children = mutableMapOf<String, Node>()
            var parameterChild: Pair<String, Node>? = null
            var wildcardChild: Node? = null
            var tailcardChild: Pair<String, Node>? = null
            val methodMatches = mutableMapOf<HttpMethod, Success>()

            fun build() = when {
                parameterChild != null -> ParameterNode(children, route, success, methodMatches, parameterChild!!)
                wildcardChild != null -> WildcardNode(children, route, success, methodMatches, wildcardChild!!)
                tailcardChild != null -> TailcardNode(children, route, success, methodMatches, tailcardChild!!)
                else -> ElementNode(children, route, success, methodMatches)
            }
        }

        private fun processChild(child: RoutingNode, accumulator: NodeBuilder): Boolean {
            @OptIn(InternalAPI::class)
            when (val s = child.selector) {
                is PathSegmentConstantRouteSelector -> {
                    accumulator.children[s.value.encodeURLPathPart()] = registerChildren(child)
                }

                is PathSegmentParameterRouteSelector,
                is PathSegmentOptionalParameterRouteSelector -> {
                    if (isPlainParameterChild(child)) {
                        accumulator.parameterChild = s.name to registerChildren(child)
                    } else {
                        return false
                    }
                }

                is HttpMethodRouteSelector -> {
                    if (child.handlers.isNotEmpty() && child.children.isEmpty()) {
                        accumulator.methodMatches[s.method] = makeFastPathSuccess(child)
                    }
                }

                is PathSegmentWildcardRouteSelector -> {
                    accumulator.wildcardChild = registerChildren(child)
                }

                is PathSegmentTailcardRouteSelector -> {
                    if (s.prefix.isEmpty()) {
                        // Consumes all remaining segments; followable when sole matcher.
                        accumulator.tailcardChild = s.name to registerChildren(child)
                    } else {
                        return false
                    }
                }

                // For "transparent" nodes like "authenticate",
                // we merge its children into the parent
                is TransparentRouteSelector -> {
                    for (grandChild in child.children) {
                        if (!processChild(grandChild, accumulator)) return false
                    }
                }

                // When unknown, we mark this node as ambiguous
                else -> return false
            }
            return true
        }

        /** Returns `true` for plain `{name}` parameter selectors with no prefix/suffix. */
        private fun isPlainParameterChild(node: RoutingNode): Boolean {
            val s = node.selector
            return s is PathSegmentParameterRouteSelector && s.prefix == null && s.suffix == null
        }
    }
}

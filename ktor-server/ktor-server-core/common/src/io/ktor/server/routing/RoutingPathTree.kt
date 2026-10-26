/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
     *
     * @throws URLDecodeException when [path] contains malformed percent-encoding, matching the
     * eager validation performed by [RoutingResolveContext] so both resolvers reject the same requests.
     */
    fun tryResolve(path: String, method: HttpMethod): Success? {
        if (root !is ElementNode) return null
        if (path.indexOf('%') >= 0) path.decodeURLPart()
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
            val end = segmentEnd(path, i, length)
            if (end == i) { // empty segment ("//"), skip
                i = end + 1
                continue
            }
            val segment = if (i == 0 && end == length) path else path.substring(i, end)
            val constantNext = current.children[segment]
            current = constantNext ?: when (current) {
                is ParameterNode -> {
                    val value = decodeSegment(segment)
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
            i = end + 1
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
     * Appends each segment in `path[start, length)` to [parameters] under [name], matching
     * [PathSegmentTailcardRouteSelector] capture semantics: empty segments (from `//`) are
     * skipped and each part is URL-decoded.
     */
    private fun captureTailcardSegments(
        addParameter: (String, String) -> Unit,
        name: String,
        path: String,
        start: Int,
        length: Int,
    ) {
        var segStart = start
        while (segStart < length) {
            val segEnd = segmentEnd(path, segStart, length)
            if (segEnd != segStart) { // skip empty segments produced by consecutive '/'
                addParameter(name, decodeSegment(path.substring(segStart, segEnd)))
            }
            segStart = segEnd + 1
        }
    }

    /** Returns the index of the next `/` at or after [start], or [length] when the segment runs to the end. */
    private fun segmentEnd(path: String, start: Int, length: Int): Int {
        var end = start
        while (end < length && path[end] != '/') end++
        return end
    }

    /** URL-decodes [segment] when it contains a `%`. The whole path is validated up front in [tryResolve]. */
    private fun decodeSegment(segment: String): String =
        if (segment.indexOf('%') < 0) segment else segment.decodeURLPart()

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

            return registerNode(listOf(currentRoot)) ?: AmbiguousNode
        }

        private fun makeFastPathSuccess(node: RoutingNode): Success =
            Success(
                route = node,
                parameters = Parameters.Empty,
                quality = RouteSelectorEvaluation.qualityConstant,
            )

        /**
         * Builds the index node for one path position.
         *
         * @nodes RoutingNodes matching the same segment. Their children are merged.
         * @returns `null` when the position cannot be resolved unambiguously.
         */
        private fun registerNode(nodes: List<RoutingNode>): Node? {
            val route = pickRoute(nodes) ?: return null
            val siblings = groupSiblings(effectiveChildren(nodes)) ?: return null

            val children = registerConstants(siblings.constants) ?: return null
            val methods = registerMethods(siblings.methods) ?: return null
            val param = siblings.params.singleOrNull()?.let { child ->
                val paramNode = registerNode(listOf(child)) ?: return null
                paramName(child) to paramNode
            }
            val wildcard = siblings.wildcards.singleOrNull()?.let { child ->
                registerNode(listOf(child)) ?: return null
            }
            val tail = siblings.tailcards.singleOrNull()?.let { child ->
                val paramNode = registerNode(listOf(child)) ?: return null
                tailcardName(child) to paramNode
            }

            val success = route.takeIf { it.handlers.isNotEmpty() }?.let(::makeFastPathSuccess)
            return when {
                param != null -> ParameterNode(children, route, success, methods, param)
                wildcard != null -> WildcardNode(children, route, success, methods, wildcard)
                tail != null -> TailcardNode(children, route, success, methods, tail)
                else -> ElementNode(children, route, success, methods)
            }
        }

        /** Picks the node whose handlers serve the position. Two nodes with handlers are ambiguous. */
        private fun pickRoute(nodes: List<RoutingNode>): RoutingNode? {
            val withHandlers = nodes.filter { it.handlers.isNotEmpty() }
            return when (withHandlers.size) {
                0 -> nodes.first()
                1 -> withHandlers.first()
                else -> null
            }
        }

        private class Siblings {
            val constants = linkedMapOf<String, MutableList<RoutingNode>>()
            val params = mutableListOf<RoutingNode>()
            val wildcards = mutableListOf<RoutingNode>()
            val tailcards = mutableListOf<RoutingNode>()
            val methods = linkedMapOf<HttpMethod, MutableList<RoutingNode>>()
        }

        /**
         * Sorts [nodes] by selector kind. Returns `null` when a selector is not supported by the index,
         * or when two siblings compete for the same dynamic slot (parameter, wildcard or tailcard).
         */
        private fun groupSiblings(nodes: List<RoutingNode>): Siblings? {
            val siblings = Siblings()
            for (node in nodes) {
                when (val selector = node.selector) {
                    is PathSegmentConstantRouteSelector -> {
                        val key = selector.value.encodeURLPathPart()
                        siblings.constants.getOrPut(key) { mutableListOf() }.add(node)
                    }

                    is PathSegmentParameterRouteSelector,
                    is PathSegmentOptionalParameterRouteSelector -> {
                        if (!isPlainParameterChild(node)) return null
                        siblings.params.add(node)
                    }

                    is HttpMethodRouteSelector -> {
                        // Children (e.g. a header check) can select a different route, so the index cannot decide.
                        if (node.children.isNotEmpty()) return null
                        if (node.handlers.isNotEmpty()) {
                            siblings.methods.getOrPut(selector.method) { mutableListOf() }.add(node)
                        }
                    }

                    is PathSegmentWildcardRouteSelector -> siblings.wildcards.add(node)

                    // Consumes all remaining segments; followable when sole matcher.
                    is PathSegmentTailcardRouteSelector -> {
                        if (selector.prefix.isNotEmpty()) return null
                        siblings.tailcards.add(node)
                    }

                    // When unknown, we mark this node as ambiguous
                    else -> return null
                }
            }

            // Multiple plain parameter siblings make scoring non-trivial — bail out.
            if (siblings.params.size > 1 || siblings.wildcards.size > 1 || siblings.tailcards.size > 1) {
                return null
            }
            return siblings
        }

        private fun registerConstants(groups: Map<String, List<RoutingNode>>): MutableMap<String, Node>? {
            val result = mutableMapOf<String, Node>()
            for ((segment, nodes) in groups) {
                result[segment] = registerNode(nodes) ?: return null
            }
            return result
        }

        private fun registerMethods(groups: Map<HttpMethod, List<RoutingNode>>): MutableMap<HttpMethod, Success>? {
            val result = mutableMapOf<HttpMethod, Success>()
            for ((method, nodes) in groups) {
                // The same method declared twice is ambiguous.
                val node = nodes.singleOrNull() ?: return null
                result[method] = makeFastPathSuccess(node)
            }
            return result
        }

        /** Returns the parameter name of a plain `{name}` selector. */
        private fun paramName(node: RoutingNode): String = (node.selector as PathSegmentParameterRouteSelector).name

        private fun tailcardName(node: RoutingNode): String = (node.selector as PathSegmentTailcardRouteSelector).name

        /**
         * Returns the children of [nodes], looking through transparent nodes.
         * Their children are treated as direct children of the parent.
         */
        @OptIn(InternalAPI::class)
        private fun effectiveChildren(nodes: List<RoutingNode>): List<RoutingNode> {
            val result = mutableListOf<RoutingNode>()
            fun collect(parents: List<RoutingNode>) {
                for (parent in parents) {
                    for (child in parent.children) {
                        if (child.selector is TransparentRouteSelector) {
                            collect(listOf(child))
                        } else {
                            result.add(child)
                        }
                    }
                }
            }
            collect(nodes)
            return result
        }

        /** Returns `true` for plain `{name}` parameter selectors with no prefix/suffix. */
        private fun isPlainParameterChild(node: RoutingNode): Boolean {
            val s = node.selector
            return s is PathSegmentParameterRouteSelector && s.prefix == null && s.suffix == null
        }
    }
}

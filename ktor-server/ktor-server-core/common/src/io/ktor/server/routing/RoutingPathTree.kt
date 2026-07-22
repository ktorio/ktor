/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.routing.RoutingResolveResult.Success

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

    /**
     * Ultra-fast lookup operating directly on the raw request [path] — without allocating
     * a [SegmentedPath], [RoutingResolveContext], or its scratch lists.
     *
     * Returns a cached [RoutingResolveResult.Success] when every segment matches a child
     * unambiguously and a terminal exists for [method]. Bails out (returns `null`) for
     * any ambiguous nodes.
     */
    fun tryResolve(path: String, method: HttpMethod): Success? {
        if (root.ambiguous) return null
        val length = path.length

        // Empty or root-only path — resolve directly at the root node.
        if (length == 0 || (length == 1 && path[0] == '/')) {
            val terminal = root.terminalResultFor(method) ?: return null
            return terminal
        }

        val hasTrailingSlash = path[length - 1] == '/'
        val effectiveEnd = if (hasTrailingSlash) length - 1 else length

        var parameters: ParametersBuilder? = null
        var current: Node = root
        var i = if (path[0] == '/') 1 else 0

        while (i < effectiveEnd) {
            if (current.ambiguous) return null
            // Scan to the next '/' or end, tracking whether '%' appears.
            var j = i
            var hasPercent = false
            while (j < effectiveEnd) {
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
            if (constantNext != null) {
                current = constantNext
            } else {
                if (current.hasFallbackSibling) return null
                val paramChild = current.parameterChild ?: return null
                val value = try {
                    if (hasPercent) segment.decodeURLPart() else segment
                } catch (_: Exception) {
                    segment
                }
                // Non-optional path parameters reject empty segments.
                if (value.isEmpty()) return null
                if (parameters == null) {
                    parameters = ParametersBuilder()
                }
                val (name, node) = paramChild
                parameters.append(name, value)
                current = node
            }
            i = j + 1
        }

        if (current.ambiguous) return null
        if (hasTrailingSlash && current.requiresExactSlashHandling) return null
        val terminal = current.terminalResultFor(method) ?: return null

        return Success(
            route = terminal.route,
            parameters = parameters?.build() ?: return null,
            quality = RouteSelectorEvaluation.qualityPathParameter,
        )
    }

    private class Node {
        val children: MutableMap<String, Node> = HashMap()

        /** Fallback parameter child, tried after constant-match misses (lower quality). */
        var parameterChild: Pair<String, Node>? = null

        /** The [RoutingNode] owning this tree node, if any. */
        var routingNode: RoutingNode? = null

        /** Pre-built result for [routingNode], reused across requests. */
        var successResult: Success? = null

        /** Pre-built results keyed by HTTP method, reused across requests. */
        val methodMatches: MutableMap<HttpMethod, Success> = HashMap()

        /**
         * When `true`, a sibling may match with quality ≥ [qualityConstant][RouteSelectorEvaluation.qualityConstant],
         * so the tree cannot safely resolve here and must defer to DFS.
         */
        var ambiguous: Boolean = false

        /**
         * When `true`, a sibling exists with quality between
         * [qualityPathParameter][RouteSelectorEvaluation.qualityPathParameter] and
         * [qualityConstant][RouteSelectorEvaluation.qualityConstant]. Constant hits
         * still win, but the tree must not fall through to [parameterChild].
         */
        var hasFallbackSibling: Boolean = false

        /** Forces fallback for trailing-slash requests this node cannot reason about. */
        var requiresExactSlashHandling: Boolean = false

        fun terminalResultFor(method: HttpMethod): Success? {
            methodMatches[method]?.let { return it }
            val node = routingNode
            if (node != null && node.handlers.isNotEmpty() && methodMatches.isEmpty()) {
                return successResult
            }
            return null
        }
    }

    companion object {
        /**
         * Builds a [RoutingPathTree] from a routing [root], covering only the subset
         * safe for fast-path resolution.
         */
        fun build(root: RoutingNode): RoutingPathTree {
            val treeRoot = Node()
            val rootSelector = root.selector
            if (rootSelector !is RootRouteSelector) {
                treeRoot.ambiguous = true
                return RoutingPathTree(treeRoot)
            }
            var entryNode = treeRoot
            for (part in rootSelector.rootParts) {
                entryNode = entryNode.children.getOrPut(part.encodeURLPathPart()) { Node() }
            }
            entryNode.routingNode = root
            if (root.handlers.isNotEmpty()) {
                entryNode.successResult = makeFastPathSuccess(root)
            }
            registerChildren(entryNode, root)
            return RoutingPathTree(treeRoot)
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
        private fun registerChildren(treeNode: Node, routingNode: RoutingNode) {
            val children = routingNode.children

            // Multiple plain parameter siblings make scoring non-trivial — bail out.
            val parameterCount = children.count { isPlainParameterChild(it) }
            if (parameterCount > 1) {
                treeNode.ambiguous = true
                return
            }

            for (child in children) {
                when (val s = child.selector) {
                    is PathSegmentConstantRouteSelector -> {
                        // Store the key pre-encoded so it matches raw (undecoded) request segments.
                        val sub = treeNode.children.getOrPut(s.value.encodeURLPathPart()) { Node() }
                        sub.routingNode = child
                        if (child.handlers.isNotEmpty()) {
                            sub.successResult = makeFastPathSuccess(child)
                        }
                        registerChildren(sub, child)
                    }

                    is PathSegmentParameterRouteSelector -> {
                        if (!isPlainParameterChild(child)) {
                            // Prefix/suffix params (quality 0.9) are fallback siblings.
                            treeNode.hasFallbackSibling = true
                        } else {
                            val sub = Node()
                            sub.routingNode = child
                            if (child.handlers.isNotEmpty()) {
                                sub.successResult = makeFastPathSuccess(child)
                            }
                            treeNode.parameterChild = s.name to sub
                            registerChildren(sub, child)
                        }
                    }

                    is HttpMethodRouteSelector -> {
                        if (child.handlers.isNotEmpty() && child.children.isEmpty()) {
                            treeNode.methodMatches[s.method] = makeFastPathSuccess(child)
                        } else {
                            // Method node with sub-routes — cannot reason about their qualities.
                            treeNode.ambiguous = true
                        }
                    }

                    is TrailingSlashRouteSelector -> {
                        treeNode.requiresExactSlashHandling = true
                    }

                    is PathSegmentWildcardRouteSelector,
                    is PathSegmentTailcardRouteSelector,
                    is PathSegmentOptionalParameterRouteSelector -> {
                        // Low-quality selectors: can't outrank constants but need DFS on miss.
                        treeNode.hasFallbackSibling = true
                    }

                    else -> {
                        // Use maxQualityHint to decide: if strictly below qualityConstant,
                        // treat as fallback; otherwise mark ambiguous.
                        val hint = child.selector.qualityUpperBound
                        if (!hint.isNaN() && hint < RouteSelectorEvaluation.qualityConstant) {
                            treeNode.hasFallbackSibling = true
                        } else {
                            treeNode.ambiguous = true
                        }
                    }
                }
            }
        }

        /** Returns `true` for plain `{name}` parameter selectors with no prefix/suffix. */
        private fun isPlainParameterChild(node: RoutingNode): Boolean {
            val s = node.selector
            return s is PathSegmentParameterRouteSelector && s.prefix == null && s.suffix == null
        }
    }
}

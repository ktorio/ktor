/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*

/**
 * A fast-path index over the routing tree, keyed by constant path segments.
 *
 * The trie short-circuits routing resolution for the common case of "static" endpoints
 * (e.g. `get("/hello") { ... }`), where the entire route consists of constant path
 * segments terminating in an [HttpMethodRouteSelector] (or a leaf with handlers).
 *
 * For any incoming request, [lookup] either:
 *  - returns a [RoutingNode] when the request can be resolved unambiguously via the trie, or
 *  - returns `null` to signal that the caller must fall back to the regular DFS resolver
 *    (because either no path matches, or the matched path has ambiguity with non-constant
 *    siblings such as parameter/wildcard/tailcard/header/host selectors).
 */
internal class RoutingPathTrie private constructor(private val root: Node) {

    /**
     * Attempts to resolve [segments] + [method] via the trie. Returns a cached
     * [RoutingResolveResult.Success] when the resolution is unambiguous, or `null` if the
     * caller must use DFS.
     *
     * The returned `Success` instance is shared across all requests that resolve to the same
     * route via the fast path (parameters are always [Parameters.Empty], quality is always
     * [RouteSelectorEvaluation.qualityConstant]), so the hot path allocates zero result objects.
     *
     * Note: this does NOT take HTTP method matching into account when ambiguity exists; it only
     * returns a fast-path match for routes whose full ancestry consists of constant path /
     * trailing-slash selectors with no sibling selectors capable of changing the outcome.
     */
    fun lookup(
        segments: List<String>,
        segmentsSize: Int,
        method: HttpMethod,
        hasTrailingSlash: Boolean,
    ): RoutingResolveResult.Success? {
        if (root.ambiguous) return null

        // Captured path parameters, lazily allocated only when a parameter-child match
        // happens during the walk.
        var capturedNames: ArrayList<String>? = null
        var capturedValues: ArrayList<String>? = null

        var current: Node = root
        var index = 0
        while (index < segmentsSize) {
            if (current.ambiguous) return null
            val segment = segments[index]
            val constantNext = current.children[segment]
            if (constantNext != null) {
                current = constantNext
            } else {
                // No constant child for this segment. The trie may still resolve via a plain
                // parameter child, BUT only if there are no fallback siblings at this node
                // that could outrank a plain parameter match (e.g. a `PathSegmentParameter`
                // with a prefix/suffix, which has quality 0.9 > plain param's 0.8). In that
                // case we must defer to the slow DFS to preserve scoring semantics.
                if (current.hasFallbackSibling) return null
                val paramChild = current.parameterChild ?: return null
                // Non-optional path parameters reject empty segments (e.g. the trailing empty
                // segment produced for `/test/`), so we must mirror that here.
                if (segment.isEmpty()) return null
                if (capturedNames == null) {
                    capturedNames = ArrayList(2)
                    capturedValues = ArrayList(2)
                }
                capturedNames.add(paramChild.parameterName)
                capturedValues!!.add(segment)
                current = paramChild.node
            }
            index++
        }

        // All segments consumed. Resolve a final terminal node (handlers + optional method).
        if (current.ambiguous) return null

        // If a trailing slash is in the URL, only TrailingSlash-extended nodes can match.
        // For simplicity, the v1 trie does not specially handle TrailingSlashRouteSelector
        // siblings (they're considered "complex" siblings and disqualify the entry).
        if (hasTrailingSlash && current.requiresExactSlashHandling) return null

        val terminal = current.terminalResultFor(method) ?: return null
        return withCapturedParameters(terminal, capturedNames, capturedValues)
    }

    /**
     * Ultra-fast lookup that operates directly on the raw, undecoded request `path` string and
     * the request `method` — without allocating a [SegmentedPath], a [RoutingResolveContext],
     * its scratch ArrayLists, or its `resolve` suspend continuation.
     *
     * Returns a cached [RoutingResolveResult.Success] when:
     *  - the trie is non-empty at the root,
     *  - the path contains no percent-encoded characters (callers must fall back to the slow
     *    path for those so decoded equality is honoured),
     *  - every segment matches a constant path child unambiguously, and
     *  - a terminal exists for [method].
     *
     * Returns `null` in every other case to signal that the caller must build a full
     * [RoutingResolveContext] and run the regular resolver.
     */
    fun tryFastResolve(path: String, method: HttpMethod): RoutingResolveResult.Success? {
        if (root.ambiguous) return null
        val length = path.length
        if (length == 0) return null

        // Defer all trailing-slash requests to the regular resolver. Trailing-slash semantics
        // in Ktor depend on the [TrailingSlashRouteSelector], [IgnoreTrailingSlash] plugin,
        // and the surrounding route shape; encoding all of this in the fast path is brittle.
        // Bailing out here is cheap and only forfeits the fast path for `/foo/`-style URLs
        // which are not the dominant hot path.
        if (length > 1 && path[length - 1] == '/') return null

        var capturedNames: ArrayList<String>? = null
        var capturedValues: ArrayList<String>? = null

        var current: Node = root
        var i = 0
        // Skip a single leading '/'.
        if (path[0] == '/') i = 1

        while (i < length) {
            if (current.ambiguous) return null
            // Find the next '/' boundary. Bail out if we see a percent-encoded byte; the
            // caller must fall back to the decoded slow path in that case.
            var j = i
            while (j < length) {
                val c = path[j]
                if (c == '/') break
                if (c == '%') return null
                j++
            }
            if (j == i) {
                // Adjacent '/' (e.g. "//"): skip the empty segment, matching `SegmentedPath`.
                i = j + 1
                continue
            }
            // The trie keys are `String`s, so we have to materialise the segment to probe
            // the children map. This is still cheaper than the full slow-path machinery.
            val segment = if (i == 0 && j == length) path else path.substring(i, j)
            val constantNext = current.children[segment]
            if (constantNext != null) {
                current = constantNext
            } else {
                // No constant child; only follow the plain parameter child when no fallback
                // sibling could outrank it (see `lookup` for the rationale).
                if (current.hasFallbackSibling) return null
                val paramChild = current.parameterChild ?: return null
                if (capturedNames == null) {
                    capturedNames = ArrayList(2)
                    capturedValues = ArrayList(2)
                }
                capturedNames.add(paramChild.parameterName)
                capturedValues!!.add(segment)
                current = paramChild.node
            }
            i = j + 1
        }

        if (current.ambiguous) return null
        if (current.requiresExactSlashHandling) return null
        val terminal = current.terminalResultFor(method) ?: return null
        return withCapturedParameters(terminal, capturedNames, capturedValues)
    }

    /**
     * Returns [terminal] when no path parameters were captured along the trie walk (in which
     * case the cached [terminal] can be reused as-is), or a fresh [RoutingResolveResult.Success]
     * carrying the captured parameters when at least one parameter child was visited. Building
     * the [Parameters] eagerly only on the parameter-route slow tail keeps the constant-path
     * hot path completely allocation-free.
     */
    private fun withCapturedParameters(
        terminal: RoutingResolveResult.Success,
        capturedNames: List<String>?,
        capturedValues: List<String>?,
    ): RoutingResolveResult.Success {
        if (capturedNames == null) return terminal
        val parameters = ParametersBuilder(capturedNames.size).apply {
            for (idx in capturedNames.indices) {
                append(capturedNames[idx], capturedValues!![idx])
            }
        }.build()
        // When at least one path parameter is captured, the effective resolve quality matches
        // what the regular [findBestRoute] would compute: the minimum across the walked
        // selectors. Constant segments contribute [qualityConstant] (1.0) and parameter
        // segments contribute [qualityPathParameter] (0.8), so the minimum is always the
        // parameter quality whenever any parameter child was visited.
        return RoutingResolveResult.Success(
            route = terminal.route,
            parameters = parameters,
            quality = RouteSelectorEvaluation.qualityPathParameter,
        )
    }

    private class Node {
        val children: MutableMap<String, Node> = HashMap()

        /**
         * Parameter-capturing child for unmatched constants under this node. Set when the
         * routing tree contains a [PathSegmentParameterRouteSelector] as a sibling alongside
         * (or instead of) constant path children. The capturing child is tried *after* an
         * exact constant match fails, mirroring how the regular DFS scoring prefers constants
         * (quality 1.0) over parameters (quality 0.8).
         */
        var parameterChild: ParameterChild? = null

        /**
         * The [RoutingNode] that "owns" this trie node (constant path leaf in the routing tree),
         * if any. May or may not have handlers itself.
         */
        var routingNode: RoutingNode? = null

        /**
         * Pre-built `Success` for [routingNode], reused across requests to avoid per-call
         * allocation. Computed lazily on first lookup and then cached.
         */
        var routingNodeResult: RoutingResolveResult.Success? = null

        /**
         * Pre-built `Success` per HTTP method leaf. The map is populated when the children of
         * [routingNode] are exclusively [HttpMethodRouteSelector] leaves with handlers. The
         * cached `Success` is reused across requests so the fast path allocates nothing.
         */
        val methodLeafResults: MutableMap<HttpMethod, RoutingResolveResult.Success> = HashMap()

        /**
         * If `true`, the trie cannot safely return a *constant* terminal match at or below
         * this node and must defer the entire request to the slow DFS. Set when a sibling
         * may produce a match with quality ≥ [RouteSelectorEvaluation.qualityConstant] (1.0)
         * — e.g. a query/header/host/accept parameter selector, an `HttpMethodRouteSelector`
         * that wraps additional sub-routes, a transparent wrapper that could hide anything,
         * a regex selector, or multiple plain parameter siblings.
         *
         * Note: siblings with strictly lower quality than `qualityConstant` (e.g. a wildcard
         * `static {}` block, a tailcard `{...}` catch-all) do **not** set this flag, because
         * Ktor's routing scoring guarantees that a successful constant lookup outranks any
         * such sibling.
         */
        var ambiguous: Boolean = false

        /**
         * If `true`, at least one sibling under this node may produce a *non-constant* match
         * with quality strictly greater than [RouteSelectorEvaluation.qualityPathParameter]
         * but less than [RouteSelectorEvaluation.qualityConstant] — e.g. a path parameter
         * selector with a `prefix`/`suffix` (quality 0.9). When this flag is set the trie may
         * still return a *constant* terminal hit (which has quality 1.0 and outranks the
         * fallback sibling), but must **not** fall through to its plain `parameterChild`
         * (whose 0.8 quality is below the sibling's 0.9). In that case the caller must defer
         * to the slow DFS so the higher-quality prefix/suffix parameter sibling can match.
         *
         * Also set when there's a *low-quality* fallback sibling alongside a plain parameter
         * child where the slow DFS still needs to run if the plain parameter mismatches.
         */
        var hasFallbackSibling: Boolean = false

        /**
         * Set to `true` when this node is reached through a route that participates in
         * trailing-slash semantics in a way the trie cannot reason about. Forces fallback
         * for requests whose URLs end with `/`.
         */
        var requiresExactSlashHandling: Boolean = false

        fun terminalResultFor(method: HttpMethod): RoutingResolveResult.Success? {
            // Method-specific leaf (e.g. `get("/hello")` creates a HttpMethod child).
            methodLeafResults[method]?.let { return it }
            // Or the constant-path node itself has handlers (e.g. `handle { }` on the path node).
            val node = routingNode
            if (node != null && node.handlers.isNotEmpty() && methodLeafResults.isEmpty()) {
                return routingNodeResult
            }
            return null
        }
    }

    /**
     * Trie node holding a parameter-capturing child and the parameter name to bind the matched
     * segment to. Each captured segment must be materialised as a fresh [Parameters] entry per
     * request (no cross-call caching is possible).
     */
    private class ParameterChild(
        val parameterName: String,
        val node: Node,
    )

    companion object {
        /**
         * Builds a [RoutingPathTrie] from a routing [root]. The resulting trie covers only the
         * subset of the routing tree that is safe for fast-path resolution; everything else is
         * left for the DFS resolver to handle.
         */
        fun build(root: RoutingNode): RoutingPathTrie {
            val trieRoot = Node()
            // Walk the root selector — it may itself be a [RootRouteSelector] with a constant
            // prefix (e.g. `application.rootPath = "/api"`), which we model as a chain of
            // constant segments at the very top of the trie.
            val rootSelector = root.selector
            if (rootSelector !is RootRouteSelector) {
                // Root has a non-constant selector — disable the entire fast path.
                trieRoot.ambiguous = true
                return RoutingPathTrie(trieRoot)
            }
            // Descend through the constant root prefix (e.g. "/api/v1").
            var entryNode = trieRoot
            for (part in rootSelector.rootParts) {
                entryNode = entryNode.children.getOrPut(part) { Node() }
            }
            entryNode.routingNode = root
            if (root.handlers.isNotEmpty()) {
                entryNode.routingNodeResult = makeFastPathSuccess(root)
            }
            registerChildren(entryNode, root)
            return RoutingPathTrie(trieRoot)
        }

        /**
         * Constructs a reusable, shared [RoutingResolveResult.Success] for [node]. The fast
         * path never captures path parameters, so the parameter set is always empty.
         */
        private fun makeFastPathSuccess(node: RoutingNode): RoutingResolveResult.Success =
            RoutingResolveResult.Success(
                route = node,
                parameters = Parameters.Empty,
                quality = RouteSelectorEvaluation.qualityConstant,
            )

        /**
         * Registers all children of [routingNode] into [trieNode].
         *
         * Children are classified into three buckets:
         *  - **Indexable children** (constant path, plain parameter, method leaf, trailing slash):
         *    inserted into [trieNode]'s structures so the trie can resolve them directly.
         *  - **Fallback children** (anything we cannot statically prove ranks above a constant
         *    match — wildcards, tailcards, parameter selectors with prefix/suffix, transparent
         *    wrappers, header/host/regex selectors, plugin selectors such as auth, etc.): the
         *    [trieNode] is marked with [Node.hasFallbackSibling] so the lookup defers to the
         *    slow path **only** when no constant child matched. The constant siblings are
         *    still indexed and remain usable for the common case (e.g. `staticResources("") +
         *    get("/hello")`, where `/hello` is a constant hit that outranks the tailcard).
         *  - **Truly ambiguous children**: multiple parameter siblings under the same node
         *    (the routing scoring rules between them are non-trivial), in which case the
         *    [trieNode] is marked [Node.ambiguous] and the entire subtree is left to DFS.
         */
        private fun registerChildren(trieNode: Node, routingNode: RoutingNode) {
            val children = routingNode.children
            // First pass: count plain parameter siblings. The trie only supports a single
            // parameter sibling under a node; with two or more, the DFS scoring rules become
            // non-trivial (which one wins?), so we bail out and mark the node ambiguous.
            var parameterCount = 0
            for (child in children) {
                if (isPlainParameterChild(child)) {
                    parameterCount++
                }
            }
            if (parameterCount > 1) {
                trieNode.ambiguous = true
                return
            }
            for (child in children) {
                when (val s = child.selector) {
                    is PathSegmentConstantRouteSelector -> {
                        val sub = trieNode.children.getOrPut(s.value) { Node() }
                        sub.routingNode = child
                        if (child.handlers.isNotEmpty()) {
                            sub.routingNodeResult = makeFastPathSuccess(child)
                        }
                        registerChildren(sub, child)
                    }
                    is PathSegmentParameterRouteSelector -> {
                        if (!isPlainParameterChild(child)) {
                            // Parameter selectors with prefix/suffix can match the same segment
                            // a plain constant would, with quality 0.9 (above plain param's 0.8
                            // but below constant's 1.0). Treat them as low-quality fallback
                            // siblings: a constant trie hit still wins, but the trie must not
                            // fall through to its plain parameter child here.
                            trieNode.hasFallbackSibling = true
                        } else {
                            // Only plain `{name}` selectors (no prefix/suffix) participate in
                            // the fast path proper.
                            val sub = Node()
                            sub.routingNode = child
                            if (child.handlers.isNotEmpty()) {
                                sub.routingNodeResult = makeFastPathSuccess(child)
                            }
                            trieNode.parameterChild = ParameterChild(s.name, sub)
                            registerChildren(sub, child)
                        }
                    }
                    is HttpMethodRouteSelector -> {
                        if (child.handlers.isNotEmpty() && child.children.isEmpty()) {
                            trieNode.methodLeafResults[s.method] = makeFastPathSuccess(child)
                        } else {
                            // The method node has further sub-routes which the trie cannot
                            // reason about (their qualities could be anything, e.g. wrapping
                            // a query parameter selector). Mark the node ambiguous so the
                            // slow DFS handles the whole subtree.
                            trieNode.ambiguous = true
                        }
                    }
                    is TrailingSlashRouteSelector -> {
                        // V1 doesn't model trailing slash routes explicitly; mark the parent
                        // node as requiring exact slash handling so that we fall back when the
                        // request URL ends with '/'.
                        trieNode.requiresExactSlashHandling = true
                    }
                    is PathSegmentWildcardRouteSelector,
                    is PathSegmentTailcardRouteSelector,
                    is PathSegmentOptionalParameterRouteSelector -> {
                        // Low-quality path selectors (wildcards, tailcards, optional path
                        // parameters): these can never outrank a constant trie hit (their max
                        // quality is qualityPathParameter = 0.8, below constant's 1.0) but the
                        // trie must defer to DFS if the constant lookup misses so they get a
                        // chance to match.
                        trieNode.hasFallbackSibling = true
                    }
                    else -> {
                        // Anything else (query/header/host/accept parameter selectors, regex
                        // selectors, authentication selectors from plugins, `Or`/`And`
                        // composite selectors, transparent wrappers, custom selectors from
                        // user code or other plugins, etc.) is generally unsafe — it may
                        // produce a match with quality ≥ qualityConstant (1.0), in which case
                        // the trie's tie-breaking would not match the DFS's "first matching
                        // route wins" rule. *Unless* the selector statically advertises a
                        // [RouteSelector.maxQualityHint] strictly below `qualityConstant`
                        // (e.g. the static-content `TailcardSelector` with quality
                        // `qualityTailcard`), in which case we know a constant trie hit will
                        // always outrank it and we can treat it as a low-quality fallback
                        // sibling. On constant-miss the lookup defers to the slow DFS so the
                        // sibling still gets a chance to match.
                        val hint = child.selector.maxQualityHint
                        if (!hint.isNaN() && hint < RouteSelectorEvaluation.qualityConstant) {
                            trieNode.hasFallbackSibling = true
                        } else {
                            trieNode.ambiguous = true
                        }
                    }
                }
            }
        }

        /**
         * Returns `true` for plain `{name}` path-segment parameter selectors (no prefix/suffix).
         * Selectors with a prefix/suffix are not safe to handle in the fast path because they
         * partially match the segment string and would need the full DFS evaluation to
         * preserve semantics with sibling constants.
         */
        private fun isPlainParameterChild(node: RoutingNode): Boolean {
            val s = node.selector
            return s is PathSegmentParameterRouteSelector && s.prefix == null && s.suffix == null
        }
    }
}

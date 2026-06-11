/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlin.math.*

private const val ROUTING_DEFAULT_CAPACITY = 16
private const val MIN_QUALITY = -Double.MAX_VALUE

/**
 * Represents a context in which routing resolution is being performed
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext)
 *
 * @param routing root node for resolution to start at
 * @param call instance of [PipelineCall] to use during resolution
 */
public class RoutingResolveContext(
    public val routing: RoutingNode,
    public val call: PipelineCall,
    private val tracers: List<(RoutingResolveTrace) -> Unit>
) {
    /**
     * List of path segments parsed out of a [call]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext.segments)
     */
    public val segments: List<String>

    /**
     * Cached segment count to avoid repeated O(n) `size` calls on the [SegmentedPath] view.
     * Exposed `internal` so that hot selectors (in [RouteSelector] et al.) can compare
     * `segmentIndex` against this value without re-traversing the path on every step.
     */
    internal val segmentsSize: Int

    /**
     * Flag showing if path ends with slash
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext.hasTrailingSlash)
     */
    public val hasTrailingSlash: Boolean

    private val trace: RoutingResolveTrace?

    // Lazily allocated by the DFS slow path. Skipped entirely when the constant-path fast
    // path resolves the request (the overwhelmingly common case for static endpoints), which
    // saves an ArrayList + backing Object[16] allocation per call. All slow-path helpers must
    // go through [resolveResult] (a non-null getter that asserts the list is initialized).
    private var resolveResultOrNull: ArrayList<RoutingResolveResult.Success>? = null
    private val resolveResult: ArrayList<RoutingResolveResult.Success>
        get() = resolveResultOrNull ?: error(
            "Slow-path scratch list accessed before slow-path entry; " +
                "this indicates a bug in the routing resolver."
        )

    private var failedEvaluation: RouteSelectorEvaluation.Failure? = RouteSelectorEvaluation.FailedPath
    private var failedEvaluationDepth = 0

    init {
        try {
            // [ApplicationRequest.path()] allocates a fresh substring on every call. Compute it
            // once here and feed it to both [parse] and the trailing-slash flag below.
            val path = call.request.path()
            hasTrailingSlash = path.endsWith('/')
            val parsed = parse(path)
            segments = parsed
            segmentsSize = parsed.size
            trace = if (tracers.isEmpty()) null else RoutingResolveTrace(call, parsed)
        } catch (cause: URLDecodeException) {
            throw BadRequestException("Url decode failed for ${call.request.uri}", cause)
        }
    }

    private fun parse(path: String): List<String> {
        if (path.isEmpty() || path == "/") return emptyList()
        // Eagerly validate URL encoding so that malformed inputs (e.g. truncated `%XX`
        // sequences) surface as a single `BadRequestException` instead of being lazily
        // detected per-segment by the routing fast path or selectors. `decodeURLPart`
        // returns the same `String` instance when the input has no percent escapes, so
        // this is allocation-free in the common case.
        path.decodeURLPart()
        // [SegmentedPath] tolerates a leading '/' (empty leading segments are skipped during
        // iteration), so we can hand it the path string as-is and avoid an extra substring
        // allocation in the common case. We only allocate a substring when [ignoreTrailingSlash]
        // is enabled AND the path actually ends with '/' — otherwise SegmentedPath would emit a
        // bogus trailing empty segment.
        if (call.ignoreTrailingSlash && path.length > 1 && path[path.length - 1] == '/') {
            return SegmentedPath(path.substring(0, path.length - 1))
        }
        return SegmentedPath(path)
    }

    /**
     * Executes resolution procedure in this context and returns [RoutingResolveResult]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext.resolve)
     */
    public suspend fun resolve(): RoutingResolveResult {
        // Try the constant-path fast path first. It returns a non-null result only when the
        // request can be resolved unambiguously without considering parameter/wildcard/header
        // or other complex selectors. We disable it when tracing is enabled so trace listeners
        // still observe the full evaluation timeline they expect.
        if (trace == null) {
            val fastPathResult = tryResolveFastPath()
            if (fastPathResult != null) {
                return fastPathResult
            }
        }

        // Only allocate the DFS scratch lists once the slow path is actually entered.
        resolveResultOrNull = ArrayList(ROUTING_DEFAULT_CAPACITY)
        // Try the synchronous DFS first when tracing is disabled. It returns the resolved
        // quality if it could complete without needing any suspending selector evaluation,
        // and `null` if it had to bail out because a selector did not provide a synchronous
        // `evaluateSync`. In the latter case we re-run the resolution using the (suspending)
        // [handleRoute] so behaviour is identical to the original implementation. The vast
        // majority of route trees consist entirely of built-in selectors and therefore stay
        // on the synchronous path, which avoids allocating a `$handleRoute$1` coroutine
        // continuation per recursive call.
        //
        // When tracing is enabled we go straight to the suspending DFS — running the sync DFS
        // first would leave partial `RoutingResolveTrace` events behind if it bailed out
        // mid-walk, and tracing is a debugging mode where the allocation savings don't matter.
        val syncResolved = trace == null &&
            handleRouteSync(routing, 0, ArrayList(), MIN_QUALITY) != null

        if (!syncResolved) {
            // Reset slow-path state and rerun via the suspending DFS to capture results from
            // any custom user selector that genuinely requires suspension. If the sync DFS
            // didn't run at all (tracing enabled) this is just a regular slow-path resolve.
            resolveResultOrNull = ArrayList(ROUTING_DEFAULT_CAPACITY)
            failedEvaluation = RouteSelectorEvaluation.FailedPath
            failedEvaluationDepth = 0
            handleRoute(routing, 0, ArrayList(), MIN_QUALITY)
        }

        val finalResult = findBestRoute()

        trace?.registerFinalResult(finalResult)
        trace?.apply { tracers.forEach { it(this) } }
        return finalResult
    }

    private fun tryResolveFastPath(): RoutingResolveResult.Success? {
        val root = routing as? RoutingRoot ?: return null
        // Pure-constant fast path: the trie returns a *cached* Success instance for the matched
        // route. No `Success`, parameters, or quality double is allocated per request. The
        // standard pipeline still has access to query parameters via `call.request.queryParameters`.
        return root.pathTrie.lookup(
            segments = segments,
            segmentsSize = segmentsSize,
            method = call.request.httpMethod,
            hasTrailingSlash = hasTrailingSlash,
        )
    }

    /**
     * Non-suspending counterpart of [handleRoute].
     *
     * Returns the resolved quality (mirroring [handleRoute]) when the entire DFS sub-walk
     * could be performed using [RouteSelector.evaluateSync], or `null` when any selector in
     * the visited subtree does not provide a synchronous evaluation and the caller must
     * therefore re-run resolution using the suspending [handleRoute]. The function bails out
     * eagerly — once it sees a selector without a sync form, it short-circuits and unwinds
     * without touching [resolveResult] or [failedEvaluation], so the caller can safely reset
     * state before re-running.
     *
     * Keeping this function non-suspending is the whole point: the Kotlin compiler does not
     * generate a `$handleRouteSync$1` continuation, so the recursive DFS allocates **zero**
     * coroutine continuation objects when every selector is synchronous (the common case for
     * built-in selectors and the request path that the user is profiling).
     *
     * Only invoked when [trace] is `null` (see [resolve]). All `trace.skip/begin/finish` calls
     * are deliberately omitted here because the sync DFS is never used while tracing is
     * active — running it speculatively would risk recording partial events that get
     * duplicated when the suspending DFS reruns.
     */
    private fun handleRouteSync(
        entry: RoutingNode,
        segmentIndex: Int,
        trait: ArrayList<RoutingResolveResult.Success>,
        matchedQuality: Double
    ): Double? {
        val evaluation = entry.selector.evaluateSync(this, segmentIndex) ?: return null

        if (evaluation is RouteSelectorEvaluation.Failure) {
            if (segmentIndex == segmentsSize) {
                updateFailedEvaluation(evaluation, trait)
            }
            return MIN_QUALITY
        }

        check(evaluation is RouteSelectorEvaluation.Success)

        if (evaluation.quality != RouteSelectorEvaluation.qualityTransparent &&
            evaluation.quality < matchedQuality
        ) {
            return MIN_QUALITY
        }

        val newIndex = segmentIndex + evaluation.segmentIncrement

        if (entry.children.isEmpty() && newIndex != segmentsSize) {
            return MIN_QUALITY
        }

        // Allocating the `Success` result is deferred until after the early-exit checks
        // above: previously these branches built a `Success` and immediately discarded it.
        val result = RoutingResolveResult.Success(entry, evaluation.parameters, evaluation.quality)

        trait.add(result)

        val hasHandlers = entry.handlers.isNotEmpty()
        var bestSucceedChildQuality: Double = MIN_QUALITY

        if (hasHandlers && newIndex == segmentsSize) {
            if (resolveResult.isEmpty() || isBetterResolve(trait)) {
                bestSucceedChildQuality = evaluation.quality
                resolveResult.clear()
                resolveResult.addAll(trait)
                failedEvaluation = null
            }
        }

        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            val childQuality = handleRouteSync(child, newIndex, trait, bestSucceedChildQuality)
                ?: return null
            if (childQuality > 0) {
                bestSucceedChildQuality = max(bestSucceedChildQuality, childQuality)
            }
        }

        trait.removeLast()

        return if (bestSucceedChildQuality > 0) evaluation.quality else MIN_QUALITY
    }

    private suspend fun handleRoute(
        entry: RoutingNode,
        segmentIndex: Int,
        trait: ArrayList<RoutingResolveResult.Success>,
        matchedQuality: Double
    ): Double {
        val evaluation = entry.selector.evaluate(this, segmentIndex)

        if (evaluation is RouteSelectorEvaluation.Failure) {
            trace?.skip(
                entry,
                segmentIndex,
                RoutingResolveResult.Failure(entry, "Selector didn't match", evaluation.failureStatusCode)
            )
            if (segmentIndex == segmentsSize) {
                updateFailedEvaluation(evaluation, trait)
            }
            return MIN_QUALITY
        }

        check(evaluation is RouteSelectorEvaluation.Success)

        if (evaluation.quality != RouteSelectorEvaluation.qualityTransparent &&
            evaluation.quality < matchedQuality
        ) {
            trace?.skip(
                entry,
                segmentIndex,
                RoutingResolveResult.Failure(entry, "Better match was already found", HttpStatusCode.NotFound)
            )
            return MIN_QUALITY
        }

        val result = RoutingResolveResult.Success(entry, evaluation.parameters, evaluation.quality)
        val newIndex = segmentIndex + evaluation.segmentIncrement

        if (entry.children.isEmpty() && newIndex != segmentsSize) {
            trace?.skip(
                entry,
                newIndex,
                RoutingResolveResult.Failure(entry, "Not all segments matched", HttpStatusCode.NotFound)
            )

            return MIN_QUALITY
        }

        trace?.begin(entry, newIndex)
        trait.add(result)

        val hasHandlers = entry.handlers.isNotEmpty()
        var bestSucceedChildQuality: Double = MIN_QUALITY

        if (hasHandlers && newIndex == segmentsSize) {
            if (resolveResult.isEmpty() || isBetterResolve(trait)) {
                bestSucceedChildQuality = evaluation.quality
                resolveResult.clear()
                resolveResult.addAll(trait)
                failedEvaluation = null
            }

            trace?.addCandidate(trait)
        }

        // iterate using indices to avoid creating iterator
        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            val childQuality = handleRoute(child, newIndex, trait, bestSucceedChildQuality)
            if (childQuality > 0) {
                bestSucceedChildQuality = max(bestSucceedChildQuality, childQuality)
            }
        }

        trait.removeLast()

        trace?.finish(entry, newIndex, result)
        return if (bestSucceedChildQuality > 0) evaluation.quality else MIN_QUALITY
    }

    private fun findBestRoute(): RoutingResolveResult {
        val finalResolve = resolveResult

        if (finalResolve.isEmpty()) {
            return RoutingResolveResult.Failure(
                routing,
                "No matched subtrees found",
                failedEvaluation?.failureStatusCode ?: HttpStatusCode.NotFound
            )
        }

        val parameters = ParametersBuilder()
        var quality = Double.MAX_VALUE

        for (index in 0..finalResolve.lastIndex) {
            val part = finalResolve[index]
            parameters.appendAll(part.parameters)

            val partQuality = if (part.quality == RouteSelectorEvaluation.qualityTransparent) {
                RouteSelectorEvaluation.qualityConstant
            } else {
                part.quality
            }

            quality = minOf(quality, partQuality)
        }

        return RoutingResolveResult.Success(finalResolve.last().route, parameters.build(), quality)
    }

    private fun isBetterResolve(new: List<RoutingResolveResult.Success>): Boolean {
        var index1 = 0
        var index2 = 0
        val currentResolve = resolveResult

        while (index1 < currentResolve.size && index2 < new.size) {
            val quality1 = currentResolve[index1].quality
            val quality2 = new[index2].quality
            if (quality1 == RouteSelectorEvaluation.qualityTransparent) {
                index1++
                continue
            }

            if (quality2 == RouteSelectorEvaluation.qualityTransparent) {
                index2++
                continue
            }

            if (quality1 != quality2) {
                return quality2 > quality1
            }

            index1++
            index2++
        }

        val firstQuality = currentResolve.count { it.quality != RouteSelectorEvaluation.qualityTransparent }
        val secondQuality = new.count { it.quality != RouteSelectorEvaluation.qualityTransparent }
        return secondQuality > firstQuality
    }

    private fun updateFailedEvaluation(
        new: RouteSelectorEvaluation.Failure,
        trait: ArrayList<RoutingResolveResult.Success>
    ) {
        val current = failedEvaluation ?: return
        if ((current.quality < new.quality || failedEvaluationDepth < trait.size) &&
            trait.all {
                it.quality == RouteSelectorEvaluation.qualityTransparent ||
                    it.quality == RouteSelectorEvaluation.qualityConstant
            }
        ) {
            failedEvaluation = new
            failedEvaluationDepth = trait.size
        }
    }
}

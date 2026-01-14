/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Context for routing resolution that matches URL paths to route handlers.
// ABOUTME: Uses thread-local pooled state on JVM to minimize per-request allocations.

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlin.math.*

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
     * Flag showing if path ends with slash
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext.hasTrailingSlash)
     */
    public val hasTrailingSlash: Boolean = call.request.path().endsWith('/')

    private val trace: RoutingResolveTrace?

    // Pooled mutable state - acquired from thread-local pool on JVM
    private val state = acquireRoutingResolveState()

    init {
        try {
            segments = parse(call.request.path())
            trace = if (tracers.isEmpty()) null else RoutingResolveTrace(call, segments)
        } catch (cause: URLDecodeException) {
            throw BadRequestException("Url decode failed for ${call.request.uri}", cause)
        }
    }

    private fun parse(path: String): List<String> {
        if (path.isEmpty() || path == "/") return emptyList()
        val length = path.length
        var beginSegment = 0
        var nextSegment = 0
        val segmentCount = path.count { it == '/' }
        val segments = ArrayList<String>(segmentCount)
        while (nextSegment < length) {
            nextSegment = path.indexOf('/', beginSegment)
            if (nextSegment == -1) {
                nextSegment = length
            }
            if (nextSegment == beginSegment) {
                // empty path segment, skip it
                beginSegment = nextSegment + 1
                continue
            }
            val segment = path.decodeURLPart(beginSegment, nextSegment)
            segments.add(segment)
            beginSegment = nextSegment + 1
        }
        if (!call.ignoreTrailingSlash && path.endsWith("/")) {
            segments.add("")
        }
        return segments
    }

    /**
     * Executes resolution procedure in this context and returns [RoutingResolveResult]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingResolveContext.resolve)
     */
    public suspend fun resolve(): RoutingResolveResult {
        handleRoute(routing, 0, MIN_QUALITY)

        val resolveResult = findBestRoute()

        trace?.registerFinalResult(resolveResult)
        trace?.apply { tracers.forEach { it(this) } }
        return resolveResult
    }

    private suspend fun handleRoute(
        entry: RoutingNode,
        segmentIndex: Int,
        matchedQuality: Double
    ) {
        val evaluation = entry.selector.evaluate(this, segmentIndex)

        if (evaluation is RouteSelectorEvaluation.Failure) {
            trace?.skip(
                entry,
                segmentIndex,
                RoutingResolveResult.Failure(entry, "Selector didn't match", evaluation.failureStatusCode)
            )
            if (segmentIndex == segments.size) {
                updateFailedEvaluation(evaluation)
            }
            state.resultQuality = MIN_QUALITY
            return
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
            state.resultQuality = MIN_QUALITY
            return
        }

        val newIndex = segmentIndex + evaluation.segmentIncrement

        if (entry.children.isEmpty() && newIndex != segments.size) {
            trace?.skip(
                entry,
                newIndex,
                RoutingResolveResult.Failure(entry, "Not all segments matched", HttpStatusCode.NotFound)
            )
            state.resultQuality = MIN_QUALITY
            return
        }

        trace?.begin(entry, newIndex)

        // Add to trait using parallel arrays (no Success allocation)
        state.traitRoutes.add(entry)
        state.traitParameters.add(evaluation.parameters)
        state.ensureTraitQualityCapacity()
        state.traitQualities[state.traitSize] = evaluation.quality
        state.traitSize++

        val hasHandlers = entry.handlers.isNotEmpty()
        var bestSucceedChildQuality: Double = MIN_QUALITY

        if (hasHandlers && newIndex == segments.size) {
            if (state.resultSize == 0 || isBetterResolve()) {
                bestSucceedChildQuality = evaluation.quality
                state.copyTraitToResult()
                state.failedEvaluation = null
            }

            trace?.addCandidate(buildTraitListForTrace())
        }

        // iterate using indices to avoid creating iterator
        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            handleRoute(child, newIndex, bestSucceedChildQuality)
            val childQuality = state.resultQuality
            if (childQuality > 0) {
                bestSucceedChildQuality = max(bestSucceedChildQuality, childQuality)
            }
        }

        // Backtrack
        state.traitSize--
        state.traitRoutes.removeLast()
        state.traitParameters.removeLast()

        // Create Success only for trace (when tracing is enabled)
        if (trace != null) {
            val traceResult = RoutingResolveResult.Success(entry, evaluation.parameters, evaluation.quality)
            trace.finish(entry, newIndex, traceResult)
        }

        state.resultQuality = if (bestSucceedChildQuality > 0) evaluation.quality else MIN_QUALITY
    }

    private fun buildTraitListForTrace(): List<RoutingResolveResult.Success> {
        return List(state.traitSize) { index ->
            RoutingResolveResult.Success(
                state.traitRoutes[index],
                state.traitParameters[index],
                state.traitQualities[index]
            )
        }
    }

    private fun findBestRoute(): RoutingResolveResult {
        if (state.resultSize == 0) {
            return RoutingResolveResult.Failure(
                routing,
                "No matched subtrees found",
                state.failedEvaluation?.failureStatusCode ?: HttpStatusCode.NotFound
            )
        }

        state.parametersBuilder.clear()
        var quality = Double.MAX_VALUE

        for (index in 0 until state.resultSize) {
            state.parametersBuilder.appendAll(state.resultParameters[index])

            val partQuality = if (state.resultQualities[index] == RouteSelectorEvaluation.qualityTransparent) {
                RouteSelectorEvaluation.qualityConstant
            } else {
                state.resultQualities[index]
            }

            quality = minOf(quality, partQuality)
        }

        return RoutingResolveResult.Success(state.resultRoutes.last(), state.parametersBuilder.build(), quality)
    }

    private fun isBetterResolve(): Boolean {
        var index1 = 0
        var index2 = 0

        while (index1 < state.resultSize && index2 < state.traitSize) {
            val quality1 = state.resultQualities[index1]
            val quality2 = state.traitQualities[index2]
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

        var firstQuality = 0
        for (i in 0 until state.resultSize) {
            if (state.resultQualities[i] != RouteSelectorEvaluation.qualityTransparent) {
                firstQuality++
            }
        }
        var secondQuality = 0
        for (i in 0 until state.traitSize) {
            if (state.traitQualities[i] != RouteSelectorEvaluation.qualityTransparent) {
                secondQuality++
            }
        }
        return secondQuality > firstQuality
    }

    private fun updateFailedEvaluation(new: RouteSelectorEvaluation.Failure) {
        val current = state.failedEvaluation ?: return
        if (current.quality < new.quality || state.failedEvaluationDepth < state.traitSize) {
            var allConstantOrTransparent = true
            for (i in 0 until state.traitSize) {
                val q = state.traitQualities[i]
                if (q != RouteSelectorEvaluation.qualityTransparent && q != RouteSelectorEvaluation.qualityConstant) {
                    allConstantOrTransparent = false
                    break
                }
            }
            if (allConstantOrTransparent) {
                state.failedEvaluation = new
                state.failedEvaluationDepth = state.traitSize
            }
        }
    }
}

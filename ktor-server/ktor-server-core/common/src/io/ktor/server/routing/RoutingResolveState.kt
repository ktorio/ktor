/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Poolable mutable state for routing resolution algorithm.
// ABOUTME: Separated from RoutingResolveContext to enable thread-local pooling and reduce per-request allocations.

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.util.*

private const val DEFAULT_CAPACITY = 4

/**
 * Mutable state used during routing resolution.
 * This class is pooled per-thread to avoid allocations on each request.
 */
internal class RoutingResolveState {
    // Backtracking state stored as parallel arrays to avoid allocating Success objects
    val traitRoutes = ArrayList<RoutingNode>(DEFAULT_CAPACITY)
    val traitParameters = ArrayList<Parameters>(DEFAULT_CAPACITY)
    var traitQualities = DoubleArray(DEFAULT_CAPACITY)
    var traitSize = 0

    // Best result storage as parallel arrays
    val resultRoutes = ArrayList<RoutingNode>(DEFAULT_CAPACITY)
    val resultParameters = ArrayList<Parameters>(DEFAULT_CAPACITY)
    var resultQualities = DoubleArray(DEFAULT_CAPACITY)
    var resultSize = 0

    // Output from recursive handleRoute call
    var resultQuality = -Double.MAX_VALUE

    var failedEvaluation: RouteSelectorEvaluation.Failure? = RouteSelectorEvaluation.FailedPath
    var failedEvaluationDepth = 0

    // Reusable ParametersBuilder to avoid allocation in findBestRoute
    val parametersBuilder = ParametersBuilder()

    /**
     * Reset state for reuse. Called when acquired from pool.
     */
    fun reset() {
        traitRoutes.clear()
        traitParameters.clear()
        traitSize = 0
        resultRoutes.clear()
        resultParameters.clear()
        resultSize = 0
        resultQuality = -Double.MAX_VALUE
        failedEvaluation = RouteSelectorEvaluation.FailedPath
        failedEvaluationDepth = 0
        parametersBuilder.clear()
    }

    fun ensureTraitQualityCapacity() {
        if (traitSize >= traitQualities.size) {
            traitQualities = traitQualities.copyOf(traitQualities.size * 2)
        }
    }

    fun copyTraitToResult() {
        resultRoutes.clear()
        resultParameters.clear()
        resultRoutes.addAll(traitRoutes)
        resultParameters.addAll(traitParameters)
        if (resultQualities.size < traitSize) {
            resultQualities = DoubleArray(maxOf(traitSize, resultQualities.size * 2))
        }
        traitQualities.copyInto(resultQualities, 0, 0, traitSize)
        resultSize = traitSize
    }
}

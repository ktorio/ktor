/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.routing

import io.ktor.routing.*
import org.junit.Test
import kotlin.test.*

internal class PathSegmentParameterRouteSelectorTest {

    @Test
    fun testEvaluateWithPrefixAndSuffixMatched() {
        val selector = PathSegmentParameterRouteSelector("param", "prefix", "suffix")

        val evaluation = selector.evaluate(listOf("prefixPARAMsuffix"), 0)

        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix)
        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.parameters["param"], "PARAM")
    }

    @Test
    fun testEvaluateWithPrefixNotMatched() {
        val selector = PathSegmentParameterRouteSelector("param", "prefix", "suffix")

        val evaluation = selector.evaluate(listOf("1prefixPARAMsuffix"), 0)

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithSuffixNotMatched() {
        val selector = PathSegmentParameterRouteSelector("param", "prefix", "suffix")

        val evaluation = selector.evaluate(listOf("prefixPARAMsuffix1"), 0)

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithoutPrefixOrSuffix() {
        val selector = PathSegmentParameterRouteSelector("param")

        val evaluation = selector.evaluate(listOf("PARAM"), 0)

        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameter)
    }

    @Test
    fun testEvaluateWithSegmentIndexOutsideOfSegments() {
        val selector = PathSegmentParameterRouteSelector("param", "prefix", "suffix")

        val evaluation = selector.evaluate(listOf("prefixPARAMsuffix1"), 1)

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }
}

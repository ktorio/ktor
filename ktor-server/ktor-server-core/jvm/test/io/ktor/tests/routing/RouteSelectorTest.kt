/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.routing

import io.ktor.routing.*
import org.junit.Test
import kotlin.test.*

internal class RouteSelectorTest {

    @Test
    fun testEvaluateWithPrefixAndSuffixMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix)
        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.parameters["param"], "PARAM")
    }

    @Test
    fun testEvaluateWithPrefixNotMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithSuffixNotMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithoutPrefixOrSuffix() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("PARAM"),
            segmentIndex = 0,
            name = "param",
            prefix = null,
            suffix = null,
            isOptional = false,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameter)
    }

    @Test
    fun testEvaluateWithSegmentIndexOutsideOfSegments() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithPrefixNotMatchedOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithSuffixNotMatchedOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithSegmentIndexOutsideOfSegmentsOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true,
            selectorHasTrailingSlash = false,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithPrefixAndSuffixMatchedTrailingSlashNotMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = true,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Failed)
    }

    @Test
    fun testEvaluateWithPrefixAndSuffixMatchedTrailingSlashNotMatchedNotLastIndex() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix", "someOtherPath"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false,
            selectorHasTrailingSlash = true,
            contextHasTrailingSlash = false
        )

        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix)
        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.parameters["param"], "PARAM")
    }
}

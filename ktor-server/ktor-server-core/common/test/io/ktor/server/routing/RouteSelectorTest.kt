/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import kotlin.test.*

internal class RouteSelectorTest {

    @Test
    fun `evaluate with prefix and suffix matched`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertTrue(evaluation is RouteSelectorEvaluation.Success)
        assertEquals(RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix, evaluation.quality)
        assertTrue(evaluation.succeeded)
        assertEquals("PARAM", evaluation.parameters["param"])
    }

    @Test
    fun `evaluate with prefix not matched`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun `evaluate with suffix not matched`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun `evaluate without prefix or suffix`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("PARAM"),
            segmentIndex = 0,
            name = "param",
            isOptional = false
        )

        assertTrue(evaluation is RouteSelectorEvaluation.Success)
        assertTrue(evaluation.succeeded)
        assertEquals(RouteSelectorEvaluation.qualityParameter, evaluation.quality)
    }

    @Test
    fun `evaluate with segment index outside of segments`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun `evaluate with prefix not matched optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun `evaluate with suffix not matched optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun `evaluate with segment index outside of segments optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun `evaluate with trailing slash and optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo", ""),
            segmentIndex = 1,
            name = "param",
            isOptional = true
        )

        assertEquals(
            RouteSelectorEvaluation
                .Success(RouteSelectorEvaluation.qualityMissing, segmentIncrement = 1),
            evaluation
        )
    }

    @Test
    fun `evaluate without trailing slash and optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo"),
            segmentIndex = 1,
            name = "param",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun `evaluate with trailing slash and non-optional`() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo", ""),
            segmentIndex = 1,
            name = "param",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }
}

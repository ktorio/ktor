/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
import io.ktor.test.constants.FLAKY_MODE_PROPERTY
import io.ktor.test.constants.FlakyTestsMode
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport

/**
 * Excludes [Flaky]-annotated tests from the default run and includes them only when the
 * [FLAKY_MODE_PROPERTY] system property asks for them:
 *  - `exclude` (the default) — skip `@Flaky` tests, run everything else.
 *  - `only` — run **only** `@Flaky` tests, skipping everything else (the `flakyTest` nightly task,
 *    so it samples the quarantined tests without dragging the whole JVM suite along).
 *  - `all` — run the full suite with `@Flaky` tests included.
 *
 * Mirrors `StressTestCondition`, but is annotation-driven and auto-registered via JUnit
 * extension autodetection (see `META-INF/services/org.junit.jupiter.api.extension.Extension`
 * and `junit.jupiter.extensions.autodetection.enabled` in build-logic `JvmConfig`), so
 * `@Flaky` on a common-source test method is honored without a per-test `@ExtendWith`.
 */
class FlakyTestCondition : ExecutionCondition {

    /**
     * Read once: the property is fixed for the lifetime of the test JVM, and the condition is
     * evaluated for every test in the module.
     */
    private val mode = FlakyTestsMode.of(System.getProperty(FLAKY_MODE_PROPERTY))

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val flaky = context.findFlaky()

        if (flaky == null) {
            return when {
                mode != FlakyTestsMode.ONLY -> ConditionEvaluationResult.enabled("Not a @Flaky test")

                context.testMethod.isPresent ->
                    ConditionEvaluationResult.disabled("Only @Flaky tests requested ($FLAKY_MODE_PROPERTY=only)")

                else ->
                    ConditionEvaluationResult.enabled("Container kept for its @Flaky tests ($FLAKY_MODE_PROPERTY=only)")
            }
        }

        return if (mode == FlakyTestsMode.EXCLUDE) {
            ConditionEvaluationResult.disabled("@Flaky test excluded (${flaky.ticket})")
        } else {
            ConditionEvaluationResult.enabled("Flaky tests enabled (${flaky.ticket})")
        }
    }

    /**
     * Finds [Flaky] on the current test method, or on its class or any of its base classes.
     *
     * The superclass lookup works because [Flaky] is meta-annotated `@JvmInherited`, which expands
     * to `java.lang.annotation.Inherited` here: JUnit walks the superclass chain only for
     * annotations carrying it. Without that, a test inheriting from an annotated base class — the
     * shared test suite pattern used across Ktor — would silently keep running.
     */
    private fun ExtensionContext.findFlaky(): Flaky? =
        AnnotationSupport.findAnnotation(element, Flaky::class.java).orElse(null)
            ?: testClass.orElse(null)?.let { AnnotationSupport.findAnnotation(it, Flaky::class.java).orElse(null) }
}

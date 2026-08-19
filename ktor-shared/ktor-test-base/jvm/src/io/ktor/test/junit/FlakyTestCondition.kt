/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport

/**
 * Excludes [Flaky]-annotated tests from the default run and includes them only when flaky tests are
 * enabled, via one of two system properties:
 *  - `enable.flaky.tests` — run the full suite with `@Flaky` tests included.
 *  - `flaky.tests.only` — run **only** `@Flaky` tests, skipping everything else (the `flakyTest`
 *    nightly task, so it samples the quarantined tests without dragging the whole JVM suite along).
 *
 * Mirrors `StressTestCondition`, but is annotation-driven and auto-registered via JUnit
 * extension autodetection (see `META-INF/services/org.junit.jupiter.api.extension.Extension`
 * and `junit.jupiter.extensions.autodetection.enabled` in build-logic `JvmConfig`), so
 * `@Flaky` on a common-source test method is honored without a per-test `@ExtendWith`.
 */
class FlakyTestCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val onlyFlaky = System.getProperty("flaky.tests.only").isFlakyEnabled()
        val flaky = context.findFlaky()

        if (flaky == null) {
            return when {
                !onlyFlaky -> ConditionEvaluationResult.enabled("Not a @Flaky test")
                context.testMethod.isPresent ->
                    ConditionEvaluationResult.disabled("Only @Flaky tests requested (flaky.tests.only)")
                else ->
                    ConditionEvaluationResult.enabled("Container kept for its @Flaky tests (flaky.tests.only)")
            }
        }

        return if (onlyFlaky || System.getProperty("enable.flaky.tests").isFlakyEnabled()) {
            ConditionEvaluationResult.enabled("flaky tests enabled (${flaky.ticket})")
        } else {
            ConditionEvaluationResult.disabled("@Flaky test excluded (${flaky.ticket})")
        }
    }

    /** A flaky-mode system property is "on" unless it is unset, `"false"`, or `"0"`. */
    private fun String?.isFlakyEnabled(): Boolean = this != null && this !in setOf("false", "0")

    /** Finds [Flaky] on the current test method, or on its class or any of its base classes. */
    private fun ExtensionContext.findFlaky(): Flaky? =
        AnnotationSupport.findAnnotation(element, Flaky::class.java).orElse(null)
            ?: testClass.orElse(null)?.findFlakyInHierarchy()

    /**
     * Searches [Flaky] up the superclass chain.
     *
     * JUnit walks superclasses only for annotations meta-annotated with
     * `java.lang.annotation.Inherited` (see `AnnotationUtils.findAnnotation`), which a Kotlin
     * annotation declared in common code cannot be. That is why `@ExtendWith` on a base class is
     * inherited but `@Flaky` isn't. Without this walk, tests inheriting from an annotated base
     * class — the shared test suite pattern used across Ktor — would silently keep running.
     */
    private fun Class<*>.findFlakyInHierarchy(): Flaky? {
        var type: Class<*>? = this
        while (type != null && type != Any::class.java) {
            AnnotationSupport.findAnnotation(type, Flaky::class.java).orElse(null)?.let { return it }
            type = type.superclass
        }
        return null
    }
}

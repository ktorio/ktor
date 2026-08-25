/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport

/** System property selecting how this run treats `@Flaky` tests. See [FlakyTestsMode]. */
internal const val FLAKY_MODE_PROPERTY = "ktor.tests.flaky"

/**
 * How a test run treats `@Flaky` tests, taken from the [FLAKY_MODE_PROPERTY] system property.
 *
 * Mirrors `FlakyTestsMode` in build-logic, which reads the same values from the Gradle property of
 * the same name and forwards them to test JVMs. The values are spelled out in both places because
 * build-logic isn't on the test classpath.
 */
internal enum class FlakyTestsMode {
    /** The default: `@Flaky` tests don't run. */
    EXCLUDE,

    /** Nightly: run `@Flaky` tests and nothing else, to track whether they still flip. */
    ONLY,

    /** Run everything, flaky included. */
    ALL;

    internal companion object {
        fun of(value: String?): FlakyTestsMode = when (value) {
            null, "", "exclude" -> EXCLUDE

            "only" -> ONLY

            "all" -> ALL

            else -> error(
                "Unexpected value '$value' of '$FLAKY_MODE_PROPERTY'. Expected one of: exclude, only, all."
            )
        }

        fun current(): FlakyTestsMode = of(System.getProperty(FLAKY_MODE_PROPERTY))
    }
}

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
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val mode = FlakyTestsMode.current()
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

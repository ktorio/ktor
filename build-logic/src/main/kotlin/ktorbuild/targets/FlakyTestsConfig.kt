/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType

/**
 * Name token marking a test as flaky on every target.
 *
 * The `@Flaky` annotation is enforced by a JUnit `ExecutionCondition`, and there is no JUnit
 * Platform on Native/JS/Wasm. A token in the test or class name is the one selector that works
 * everywhere, because Gradle test filtering applies to every `AbstractTestTask`.
 */
const val FLAKY_NAME_TOKEN = "_flaky"

/** Matches the token anywhere in the test name, which also covers the `[target]` name suffixes. */
private const val FLAKY_TEST_PATTERN = "*$FLAKY_NAME_TOKEN*"

private const val FLAKY_MODE_PROPERTY = "ktor.tests.flaky"

/** How test tasks treat tests marked with [FLAKY_NAME_TOKEN]. Selected by [FLAKY_MODE_PROPERTY]. */
internal enum class FlakyTestsMode {
    /** The default: flaky tests don't run. */
    EXCLUDE,

    /** Nightly: run flaky tests and nothing else, to track whether they still flip. */
    ONLY,

    /** Run everything, flaky included. */
    ALL;

    companion object {
        fun of(value: String?): FlakyTestsMode = when (value) {
            null, "", "exclude" -> EXCLUDE
            "only" -> ONLY
            "all" -> ALL
            else -> error(
                "Unexpected value '$value' of '$FLAKY_MODE_PROPERTY'. Expected one of: exclude, only, all."
            )
        }
    }
}

internal fun Project.flakyTestsMode(): FlakyTestsMode =
    FlakyTestsMode.of(providers.gradleProperty(FLAKY_MODE_PROPERTY).orNull)

/**
 * Applies the [FLAKY_MODE_PROPERTY] mode to every test task of the project, on every target.
 *
 * The dedicated `flakyTest` task is left alone: it enables `@Flaky` tests through the JUnit
 * condition, which selects by annotation rather than by name.
 */
internal fun Project.configureFlakyTests() {
    val mode = flakyTestsMode()

    tasks.withType<AbstractTestTask>().configureEach {
        if (name != FLAKY_TEST_TASK) selectFlakyTests(mode)
    }
}

internal const val FLAKY_TEST_TASK = "flakyTest"

private fun AbstractTestTask.selectFlakyTests(mode: FlakyTestsMode) {
    when (mode) {
        FlakyTestsMode.EXCLUDE -> filter.excludeTestsMatching(FLAKY_TEST_PATTERN)

        FlakyTestsMode.ONLY -> {
            filter.includeTestsMatching(FLAKY_TEST_PATTERN)
            // Most modules have no flaky tests at all, and that isn't a failure.
            filter.isFailOnNoMatchingTests = false
        }

        FlakyTestsMode.ALL -> Unit
    }
}

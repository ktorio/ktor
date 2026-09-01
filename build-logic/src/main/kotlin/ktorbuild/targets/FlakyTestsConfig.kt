/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import io.ktor.test.constants.FLAKY_MODE_PROPERTY
import io.ktor.test.constants.FLAKY_NAME_TOKEN
import io.ktor.test.constants.FlakyTestsMode
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType

/** Matches the token anywhere in the test name, which also covers the `[target]` name suffixes. */
private const val FLAKY_TEST_PATTERN = "*$FLAKY_NAME_TOKEN*"

internal const val FLAKY_TEST_TASK = "flakyTest"

internal fun Project.flakyTestsMode(): FlakyTestsMode =
    FlakyTestsMode.of(providers.gradleProperty(FLAKY_MODE_PROPERTY).orNull)

/**
 * Test tasks that select `@Flaky` by annotation and must therefore *not* also get the name filter.
 *
 * Exactly the tasks `JvmConfig` gives [FLAKY_MODE_PROPERTY] and the JUnit autodetection that
 * registers `FlakyTestCondition`. Stacking both selectors on them would *intersect* rather than
 * agree: [FlakyTestsMode.ONLY] would run only the tests that are both annotated **and** named
 * `*_flaky*`, silently skipping every `@Flaky` test that carries no name token.
 *
 * Matching by name rather than by task type matters: Android host tests are `Test` tasks too, but
 * they never receive the condition, so exempting them by type would leave them with no selector at
 * all and run quarantined tests in the default Android suite.
 */
private val ANNOTATION_DRIVEN_TEST_TASKS = setOf("jvmTest", "stressTest", FLAKY_TEST_TASK)

/**
 * Applies the [FLAKY_MODE_PROPERTY] mode to every test task that cannot select `@Flaky` by
 * annotation — every target without a JUnit Platform, plus JVM tasks outside
 * [ANNOTATION_DRIVEN_TEST_TASKS]. The name token exists only for those.
 */
internal fun Project.configureFlakyTests() {
    val mode = flakyTestsMode()

    tasks.withType<AbstractTestTask>().configureEach {
        if (name !in ANNOTATION_DRIVEN_TEST_TASKS) selectFlakyTests(mode)
    }
}

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

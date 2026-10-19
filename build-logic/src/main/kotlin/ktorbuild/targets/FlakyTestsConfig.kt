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

/** Test tasks that select `@Flaky` by annotation and must therefore *not* also get the name filter. */
internal val ANNOTATION_DRIVEN_TEST_TASKS = setOf("jvmTest", "stressTest", FLAKY_TEST_TASK)

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
            filter.isFailOnNoMatchingTests = false
        }

        FlakyTestsMode.ALL -> Unit
    }
}

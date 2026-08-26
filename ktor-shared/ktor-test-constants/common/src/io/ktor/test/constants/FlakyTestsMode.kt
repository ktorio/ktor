/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.constants

/**
 * Name token marking a test as flaky on every target.
 *
 * The `@Flaky` annotation is enforced by a JUnit `ExecutionCondition`, and there is no JUnit
 * Platform on Native/JS/Wasm. A token in the test or class name is the one selector that works
 * everywhere, because Gradle test filtering applies to every test task.
 */
const val FLAKY_NAME_TOKEN = "_flaky"

/**
 * Selects how a test run treats flaky tests.
 *
 * The build reads it as a Gradle property (`-Pktor.tests.flaky=only`) and forwards it to test JVMs
 * as a system property of the same name, so there is one spelling to remember for both selectors.
 */
const val FLAKY_MODE_PROPERTY = "ktor.tests.flaky"

/** How a test run treats flaky tests, as selected by [FLAKY_MODE_PROPERTY]. */
enum class FlakyTestsMode {
    /** The default: flaky tests don't run. */
    EXCLUDE,

    /** Nightly: run flaky tests and nothing else, to track whether they still flip. */
    ONLY,

    /** Run everything, flaky included. */
    ALL;

    /** The [FLAKY_MODE_PROPERTY] value denoting this mode, as accepted by [of]. */
    val propertyValue: String get() = name.lowercase()

    companion object {
        /** Parses a [FLAKY_MODE_PROPERTY] value, treating a missing or blank one as [EXCLUDE]. */
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

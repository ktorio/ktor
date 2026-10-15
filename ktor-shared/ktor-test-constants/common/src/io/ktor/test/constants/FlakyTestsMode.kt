/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.constants

const val FLAKY_NAME_TOKEN = "_flaky"
const val FLAKY_MODE_PROPERTY = "ktor.tests.flaky"

/** How a test run treats flaky tests, as selected by [FLAKY_MODE_PROPERTY]. */
enum class FlakyTestsMode {
    EXCLUDE,
    ONLY,
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

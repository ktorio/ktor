/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

internal sealed interface LogPredicate

internal class Optional(val value: String) : LogPredicate {
    override fun toString() = "Optional: $value"
}
internal class Changing(val value: String) : LogPredicate {
    override fun toString() = "Changing: $value"
}
internal class Line(val value: String) : LogPredicate {
    override fun toString() = value
}
internal class Somewhere(val value: String) : LogPredicate {
    override fun toString(): String = "Somewhere: $value"
}

internal class LoggerDsl {
    private val predicates: MutableList<LogPredicate> = mutableListOf()

    /**
     * Expect exact entry match.
     */
    fun line(value: String) {
        predicates += Line(value)
    }

    /**
     * Expect line to be present exact in order but content can change.
     */
    fun changing(value: String) {
        predicates += Changing(value)
    }

    /**
     * Can be present or missing.
     */
    fun optional(value: String) {
        predicates += Optional(value)
    }

    /**
     * Should be present somewhere.
     */
    fun somewhere(value: String) {
        predicates += Somewhere(value)
    }

    fun build(): LogMatcher = LogMatcher(predicates)
}

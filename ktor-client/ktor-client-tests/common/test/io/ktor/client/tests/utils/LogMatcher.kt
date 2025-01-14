/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.atomicfu.atomic
import kotlin.test.assertEquals

internal class LogMatcher(
    private val originPredicates: MutableList<LogPredicate>
) {
    private val stashed = originPredicates.filterIsInstance<Somewhere>().map { it.value }.toMutableSet()
    private val predicates = originPredicates.filter { it !is Somewhere }
    private var index = 0
    private val log = mutableListOf<String>()
    private var matchFailCause: Throwable? by atomic(null)

    fun match(value: String) {
        matchFailCause?.let { throw it }

        val cause = kotlin.runCatching {
            for (line in value.lines()) {
                log += line
                matchLine(line)
            }
        }.exceptionOrNull() ?: return

        matchFailCause = cause
    }

    private fun matchLine(value: String) {
        if (value in stashed) {
            stashed.remove(value)
            return
        }

        if (index >= predicates.size) {
            fail("Too many lines logged")
        }

        when (val predicate = predicates[index++]) {
            is Changing -> return
            is Line -> if (predicate.value.lowercase() != value.lowercase()) {
                fail("Line doesn't match")
            }
            is Optional -> {
                if (predicate.value.lowercase() == value.lowercase()) return
                matchLine(value)
            }

            is Somewhere -> error("It's impossible")
        }
    }

    private fun renderExpectedLog() = buildString {
        for (predicate in originPredicates) {
            appendLine(predicate)
        }
    }

    fun finish() {
        matchFailCause?.let { throw it }

        if (predicates.size != index) {
            fail("Log size doesn't match")
        }

        if (stashed.isNotEmpty()) {
            fail("Not all stashed matched")
        }
    }

    private fun fail(message: String) {
        assertEquals(renderExpectedLog(), log.joinToString("\n"), message)
    }
}

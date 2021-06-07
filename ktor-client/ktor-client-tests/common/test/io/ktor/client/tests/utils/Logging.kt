/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils

import io.ktor.client.features.logging.*
import io.ktor.util.collections.*
import kotlin.test.*

/**
 * Test logger that provides ability to verify it's content after test.
 * The [expectedLog] contains expected log entries
 * optionally prepended with control prefixes. The following prefixes are supported:
 * - "???" means that the log entry is optional and could be missing
 * - "!!!" the log entry is flaky: it's required but it's content is changing
 * - "+++" the log entry is required but the exact place is not known
 */
internal class TestLogger(private vararg val expectedLog: String) : Logger {
    private val log = ConcurrentList<String>()

    override fun log(message: String) {
        log += message
    }

    fun reset() {
        log.clear()
    }

    fun verify() {
        var expectedIndex = 0
        var actualIndex = 0

        val message = StringBuilder()
        val stashed = ArrayList<String>()

        while (expectedIndex < expectedLog.size && actualIndex < log.size) {
            var expected = expectedLog[expectedIndex].toLowerCase()
            val actual = log[actualIndex].toLowerCase()

            var flaky = false
            var optional = false

            if (expected.startsWith("!!!")) {
                expected = expected.substring(3)
                flaky = true
            }

            if (expected.startsWith("???")) {
                expected = expected.substring(3)
                optional = true
            }

            if (expected.startsWith("+++")) {
                stashed.add(expected.drop(3))
                expectedIndex++
                continue
            }

            if (expected == actual || flaky) {
                expectedIndex++
                actualIndex++
                continue
            }

            if (actual in stashed) {
                stashed.remove(actual)
                actualIndex++
                continue
            }

            if (optional) {
                expectedIndex++
                continue
            }

            if (!expected.equals(actual, ignoreCase = true)) {
                message.appendLine(">>> Expected log:")
                expectedLog.forEach {
                    message.appendLine(it)
                }

                message.appendLine(">>> Actual log:")
                log.forEach {
                    message.appendLine(it)
                }

                message.appendLine(
                    "Expected log doesn't match actual at lines: expected $expectedIndex, actual $actualIndex"
                )
                message.appendLine("Expected: $expected")
                message.appendLine("Actual: $actual")

                fail(message.toString())
            }
        }

        while (actualIndex < log.size && stashed.isNotEmpty()) {
            val actual = log[actualIndex].toLowerCase()
            if (actual in stashed) {
                actualIndex++
                stashed.remove(actual)
            } else {
                break
            }
        }

        if (actualIndex < log.size) {
            message.append("Actual log was not fully processed:\n")
            message.appendLog(log.subList(actualIndex, log.size))
        }

        if (expectedIndex < expectedLog.size) {
            message.append("Expected log was not fully processed:\n")
            message.appendLog(expectedLog.asList().subList(expectedIndex, expectedLog.size))
        }

        if (stashed.isNotEmpty()) {
            message.append("Expected log entries were not encountered:")
            message.appendLog(stashed)
        }

        if (message.isNotEmpty()) {
            fail(message.toString())
        }
    }
}

private fun StringBuilder.appendLog(log: List<String>) {
    for (line in log) {
        append('"')
        append(log)
        append('"')
        append(',')
        append('\n')
    }
}

internal class CustomError(override val message: String) : Throwable() {
    override fun toString(): String = "CustomError[$message]"
}

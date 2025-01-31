/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.test.base

import io.ktor.client.engine.*
import io.ktor.test.*
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal expect val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>>
internal expect val platformName: String
internal expect fun platformDumpCoroutines()
internal expect fun platformWaitForAllCoroutines()

private typealias ClientTestFailure = TestFailure<HttpClientEngineFactory<*>>

/**
 * Helper interface to test clients.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader)
 */
abstract class ClientLoader(private val timeout: Duration = 1.minutes) {
    /**
     * Perform test against all clients from dependencies.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.clientTests)
     */
    fun clientTests(
        rule: EngineSelectionRule = EngineSelectionRule { true },
        retries: Int = 1,
        timeout: Duration = this.timeout,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult {
        val (selectedEngines, skippedEngines) = enginesToTest
            .partition { rule.shouldRun(it.engineName) }
        val reporter = TestReporter()

        return runTestWithData(
            selectedEngines,
            timeout = timeout,
            retries = retries,
            afterEach = { result -> reporter.testResult(result, retries) },
            afterAll = {
                reporter.skippedEngines(skippedEngines)
                reporter.flush()
            },
            handleFailures = ::aggregatedAssertionError,
        ) { (engine, retry) ->
            reporter.testTry(engine, retry, retries)
            performTestWithEngine(engine, this@ClientLoader, block)
        }
    }

    private fun aggregatedAssertionError(failures: List<ClientTestFailure>): Nothing {
        val message = buildString {
            val engineNames = failures.map { it.testCase.data.engineName }
            if (failures.size > 1) {
                appendLine("Test failed for engines: ${engineNames.joinToString()}")
            }
            failures.forEachIndexed { index, (_, cause) ->
                appendLine("Test failed for engine '$platformName:${engineNames[index]}' with:")
                appendLine(cause.stackTraceToString().prependIndent("  "))
            }
        }
        throw AssertionError(message)
    }

    /**
     * Print coroutines in debug mode.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.dumpCoroutines)
     */
    fun dumpCoroutines(): Unit = platformDumpCoroutines()

    // Issues to fix before unlocking:
    // 1. Pinger & Ponger in ws
    // 2. Nonce generator
    // @After
    fun waitForAllCoroutines(): Unit = platformWaitForAllCoroutines()

    /**
     * Defines that test should be executed only with the specified [engine].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.only)
     */
    fun only(engine: String): EngineSelectionRule {
        val pattern = EnginePattern.parse(engine)
        return EngineSelectionRule { pattern.matches(it) }
    }

    /**
     * Includes the set of [engines] for the test
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.only)
     */
    fun only(vararg engines: String): EngineSelectionRule {
        val includePatterns = engines.map(EnginePattern::parse)
        return EngineSelectionRule { engineName -> includePatterns.any { it.matches(engineName) } }
    }

    /**
     * Excludes the specified [engines] from test execution.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.except)
     */
    fun except(vararg engines: String): EngineSelectionRule = except(engines.asList())

    /**
     * Excludes the specified [engines] from test execution.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.ClientLoader.except)
     */
    fun except(engines: List<String>): EngineSelectionRule {
        val skipPatterns = engines.map(EnginePattern::parse)
        return EngineSelectionRule { engineName -> skipPatterns.none { it.matches(engineName) } }
    }

    private class TestReporter {
        private val lines: MutableList<String> = mutableListOf()
        private var pendingLine = ""

        fun skippedEngines(skippedEngines: List<HttpClientEngineFactory<*>>) {
            if (skippedEngines.isNotEmpty()) {
                message("⊘ Skipped engines: ${skippedEngines.joinToString { it.engineName }}")
            }
        }

        fun testTry(engine: HttpClientEngineFactory<*>, retry: Int, maxRetries: Int) {
            if (retry == 0) {
                message("▶ Run with ${engine.engineName}".padEnd(DESCRIPTION_COLUMN_WIDTH), flush = false)
            } else {
                printTriesCounter(retry, maxRetries)
            }
        }

        fun testResult(testResult: TestExecutionResult<*>, maxRetries: Int) {
            val retry = testResult.testCase.retry
            val cause = (testResult as? TestFailure<*>)?.cause

            val isFirstRetry = cause != null && retry == 0 && maxRetries > 0
            if (isFirstRetry) {
                message(flush = true)
                printTriesCounter(retry, maxRetries)
            }

            printStatus(testResult, maxRetries)
            if (cause != null) message("    └─ ${cause.message}")
        }

        private fun printTriesCounter(retry: Int, maxRetries: Int) {
            message("  [${retry + 1}/${maxRetries + 1}]".padEnd(DESCRIPTION_COLUMN_WIDTH), flush = false)
        }

        private fun printStatus(testResult: TestExecutionResult<*>, maxRetries: Int) {
            val status = when {
                testResult is TestSuccess -> PASSED
                testResult.testCase.retry == maxRetries -> FAILED
                else -> RETRY
            }
            message("$status (${testResult.duration.format()})")
        }

        private fun Duration.format(): String = when {
            this < 1.seconds -> toString(DurationUnit.MILLISECONDS)
            else -> toString()
        }

        private fun message(message: String = "", flush: Boolean = true) {
            pendingLine += message
            if (flush) {
                lines.add(pendingLine)
                pendingLine = ""
            }
        }

        fun flush() {
            println(lines.joinToString("\n"))
            lines.clear()
        }

        private companion object {
            private const val PASSED = "✓ PASSED"
            private const val FAILED = "✕ FAILED"
            private const val RETRY = "• FAILED"
            private const val DESCRIPTION_COLUMN_WIDTH = 20
        }
    }
}

internal val HttpClientEngineFactory<*>.engineName: String
    get() = this.toString()

/**
 * Decides whether an engine should be tested or not.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.EngineSelectionRule)
 *
 * @see ClientLoader.except
 * @see ClientLoader.only
 */
fun interface EngineSelectionRule {
    fun shouldRun(engineName: String): Boolean
}

private data class EnginePattern(
    val matchingPlatform: String?, // null means * or empty
    val matchingEngine: String?, // null means * or empty
) {
    fun matches(engineName: String): Boolean {
        var result = true
        if (matchingEngine != null) {
            result = result && engineName.lowercase() == matchingEngine
        }
        if (result && matchingPlatform != null) {
            result = result && platformName.startsWith(matchingPlatform)
        }
        return result
    }

    companion object {
        fun parse(pattern: String): EnginePattern {
            val parts = pattern.lowercase().split(":").map { it.takeIf { it != "*" } }
            val platform: String?
            val engine: String?
            when (parts.size) {
                1 -> {
                    platform = null
                    engine = parts[0]
                }

                2 -> {
                    platform = parts[0]
                    engine = parts[1]
                }

                else -> error("Engine pattern should consist of two parts: PLATFORM:ENGINE or ENGINE")
            }

            if (platform == null && engine == null) {
                error("Engine pattern should consist of two parts: PLATFORM:ENGINE or ENGINE")
            }
            return EnginePattern(platform, engine)
        }
    }
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

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
 */
abstract class ClientLoader(private val timeout: Duration = 1.minutes) {
    /**
     * Perform test against all clients from dependencies.
     */
    fun clientTests(
        rule: EngineSelectionRule = EngineSelectionRule { true },
        retries: Int = 1,
        timeout: Duration = this.timeout,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult {
        val (selectedEngines, skippedEngines) = enginesToTest
            .partition { rule.shouldRun(it.engineName) }

        return runTestWithData(
            selectedEngines,
            timeout = timeout,
            retries = retries,
            afterEach = { result -> TestReporter.testResult(result, retries) },
            afterAll = { TestReporter.skippedEngines(skippedEngines) },
            handleFailures = ::aggregatedAssertionError,
        ) { (engine, retry) ->
            TestReporter.testTry(engine, retry, retries)
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
     */
    fun dumpCoroutines(): Unit = platformDumpCoroutines()

    // Issues to fix before unlocking:
    // 1. Pinger & Ponger in ws
    // 2. Nonce generator
    // @After
    fun waitForAllCoroutines(): Unit = platformWaitForAllCoroutines()

    /** Defines that test should be executed only with the specified [engine]. */
    fun only(engine: String): EngineSelectionRule {
        val lowercaseEngineName = engine.lowercase()
        return EngineSelectionRule { it.lowercase() == lowercaseEngineName }
    }

    /** Excludes the specified [engines] from test execution. */
    fun except(vararg engines: String): EngineSelectionRule = except(engines.asList())

    /** Excludes the specified [engines] from test execution. */
    fun except(engines: List<String>): EngineSelectionRule {
        val skipPatterns = engines.map(SkipEnginePattern::parse)
        return EngineSelectionRule { engineName -> skipPatterns.none { it.matches(engineName) } }
    }

    private object TestReporter {
        private const val PASSED = "✓ PASSED"
        private const val FAILED = "✕ FAILED"
        private const val RETRY = "• FAILED"
        private const val DESCRIPTION_COLUMN_WIDTH = 20

        fun skippedEngines(skippedEngines: List<HttpClientEngineFactory<*>>) {
            if (skippedEngines.isNotEmpty()) println("⊘ Skipped engines: ${skippedEngines.joinToString { it.engineName }}")
        }

        fun testTry(engine: HttpClientEngineFactory<*>, retry: Int, retries: Int) {
            if (retry == 0) {
                print("▶ Run with ${engine.engineName}".padEnd(DESCRIPTION_COLUMN_WIDTH))
            } else {
                printTriesCounter(retry, retries)
            }
        }

        fun testResult(testResult: TestExecutionResult<*>, maxRetries: Int) {
            val retry = testResult.testCase.retry
            val status = when {
                testResult is TestSuccess -> PASSED
                retry == maxRetries -> FAILED
                else -> RETRY
            } + " (${testResult.duration.format()})"

            val cause = (testResult as? TestFailure<*>)?.cause
            if (cause != null && retry == 0 && maxRetries > 0) {
                println()
                printTriesCounter(retry, maxRetries)
            }
            println(status)
            if (cause != null) println("    └─ ${cause.message}")
        }

        private fun Duration.format(): String = if (this < 1.seconds) toString(DurationUnit.MILLISECONDS) else toString()

        private fun printTriesCounter(retry: Int, maxRetries: Int) {
            print("  [${retry + 1}/${maxRetries + 1}]".padEnd(DESCRIPTION_COLUMN_WIDTH))
        }
    }
}

internal val HttpClientEngineFactory<*>.engineName: String
    get() = this::class.simpleName!!

/**
 * Decides whether an engine should be tested or not.
 * @see ClientLoader.except
 * @see ClientLoader.only
 */
fun interface EngineSelectionRule {
    fun shouldRun(engineName: String): Boolean
}

private data class SkipEnginePattern(
    val skippedPlatform: String?, // null means * or empty
    val skippedEngine: String?, // null means * or empty
) {
    fun matches(engineName: String): Boolean {
        var result = true
        if (skippedEngine != null) {
            result = result && engineName.lowercase() == skippedEngine
        }
        if (result && skippedPlatform != null) {
            result = result && platformName.startsWith(skippedPlatform)
        }
        return result
    }

    companion object {
        fun parse(pattern: String): SkipEnginePattern {
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

                else -> error("Skip engine pattern should consist of two parts: PLATFORM:ENGINE or ENGINE")
            }

            if (platform == null && engine == null) {
                error("Skip engine pattern should consist of two parts: PLATFORM:ENGINE or ENGINE")
            }
            return SkipEnginePattern(platform, engine)
        }
    }
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import io.ktor.test.*
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal expect val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>>
internal expect val platformName: String
internal expect fun platformDumpCoroutines()
internal expect fun platformWaitForAllCoroutines()

private typealias ClientTestFailure = TestFailure<HttpClientEngineFactory<*>>

/**
 * Helper interface to test client.
 */
abstract class ClientLoader(private val timeout: Duration = 1.minutes) {
    /**
     * Perform test against all clients from dependencies.
     */
    fun clientTests(
        skipEngines: List<String> = emptyList(),
        onlyWithEngine: String? = null,
        retries: Int = 1,
        timeout: Duration = this.timeout,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): TestResult {
        val skipPatterns = skipEngines.map(SkipEnginePattern::parse)
        val (selectedEngines, skippedEngines) = enginesToTest
            .partition { shouldRun(it.engineName, skipPatterns, onlyWithEngine) }
        if (skippedEngines.isNotEmpty()) println("Skipped engines: ${skippedEngines.joinToString { it.engineName }}")

        return runTestWithData(
            selectedEngines,
            timeout = timeout,
            retries = retries,
            handleFailures = ::aggregatedAssertionError,
        ) { (engine, retry) ->
            val retrySuffix = if (retry > 0) " [$retry]" else ""
            println("Run test with engine ${engine.engineName}$retrySuffix")
            performTestWithEngine(engine, this@ClientLoader, block)
        }
    }

    private fun aggregatedAssertionError(failures: List<ClientTestFailure>): Nothing {
        val message = buildString {
            val engineNames = failures.map { it.data.engineName }
            if (failures.size > 1) {
                appendLine("Test failed for engines: ${engineNames.joinToString()}")
            }
            failures.forEachIndexed { index, (cause, _) ->
                appendLine("Test failed for engine '$platformName:${engineNames[index]}' with:")
                appendLine(cause.stackTraceToString().prependIndent("  "))
            }
        }
        throw AssertionError(message)
    }

    private fun shouldRun(
        engineName: String,
        skipEnginePatterns: List<SkipEnginePattern>,
        onlyWithEngine: String?
    ): Boolean {
        val lowercaseEngineName = engineName.lowercase()
        if (onlyWithEngine != null && onlyWithEngine.lowercase() != lowercaseEngineName) return false

        skipEnginePatterns.forEach {
            if (it.matches(lowercaseEngineName)) return false
        }

        return true
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
}

internal val HttpClientEngineFactory<*>.engineName: String
    get() = this.toString()

private data class SkipEnginePattern(
    val skippedPlatform: String?, // null means * or empty
    val skippedEngine: String?, // null means * or empty
) {
    fun matches(engineName: String): Boolean {
        var result = true
        if (skippedEngine != null) {
            result = result && engineName == skippedEngine
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

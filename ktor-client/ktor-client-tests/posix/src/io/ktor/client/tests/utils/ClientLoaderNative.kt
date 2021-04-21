/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.*

private class TestFailure(val name: String, val cause: Throwable) {
    override fun toString(): String = buildString {
        appendLine("Test failed with engine: $name")
        appendLine(cause)
        for (stackline in cause.getStackTrace()) {
            appendLine("\t$stackline")
        }
    }
}

/**
 * Helper interface to test client.
 */
public actual abstract class ClientLoader actual constructor(private val timeoutSeconds: Int) {
    /**
     * Perform test against all clients from dependencies.
     */
    public actual fun clientTests(
        skipEngines: List<String>,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        if (skipEngines.any { it.startsWith("native") }) return

        val skipEnginesLowerCase = skipEngines.map { it.toLowerCase() }.toSet()
        val filteredEngines = engines.filter {
            val name = it.toString().toLowerCase()
            !skipEnginesLowerCase.contains(name) && !skipEnginesLowerCase.contains("native:$name")
        }

        val failures = mutableListOf<TestFailure>()
        for (engine in filteredEngines) {
            val result = runCatching {
                testWithEngine(engine) {
                    withTimeout(timeoutSeconds.toLong() * 1000L) {
                        block()
                    }
                }
            }

            if (result.isFailure) {
                failures += TestFailure(engine.toString(), result.exceptionOrNull()!!)
            }
        }

        if (failures.isEmpty()) {
            return
        }

        error(failures.joinToString("\n"))
    }

    public actual fun dumpCoroutines() {
        error("Debug probes unsupported native.")
    }
}

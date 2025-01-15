/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.test.*
import io.ktor.test.dispatcher.*
import io.ktor.test.junit.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.test.TestResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@CoroutinesTimeout(5 * 60 * 1000)
@ErrorCollectorTest
actual abstract class BaseTest actual constructor() {
    actual open val timeout: Duration = 60.seconds // not used

    private val errorCollector = ErrorCollector()

    var testMethod: Optional<Method> = Optional.empty()
    var testName: String = ""

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        val method = testInfo.testMethod
        testMethod = method
        testName = method.map { it.name }.orElse(testInfo.displayName)
    }

    actual open fun afterTest() {
        errorCollector.throwErrorIfPresent()
    }

    actual open fun beforeTest() {
    }

    actual fun collectUnhandledException(error: Throwable) {
        errorCollector += error
    }

    actual fun runTest(
        timeout: Duration,
        retries: Int,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult = retryTest(retries) { retry ->
        runTestWithRealTime(CoroutineName("test-$testName"), timeout) {
            if (retry > 0) println("[Retry $retry/$retries]")
            beforeTest()
            try {
                block()
            } finally {
                afterTest()
            }
        }
    }
}

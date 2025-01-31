/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.writer

// Use the absolute path here,
// otherwise you'll get a copy in every module when running many tests,
// because gradlew kicks off a new process for every module test suite
private const val OUTPUT = "invocations.txt"

public data class Invocation(
    val instance: Int,
    val operation: String,
    val coroutineName: String,
)

public data class ThreadDetails(
    val stackTrace: List<StackTraceElement>,
    val testName: String,
    val timings: Set<Long>,
) {
    internal companion object {
        internal fun of(stackTrace: Array<StackTraceElement>): ThreadDetails =
            ThreadDetails(
                stackTrace.drop(2).take(20),
                stackTrace.getTestName(),
                setOf(System.currentTimeMillis() - start)
            )
    }

    internal fun addTime() =
        ThreadDetails(stackTrace, testName, timings + (System.currentTimeMillis() - start))
}

private fun Array<StackTraceElement>.getTestName(): String =
    firstNotNullOfOrNull {
        testRegex.find(it.className)?.value
    } ?: "(Unknown test)"

private val testRegex = Regex("\\w+?[A-Z][a-z]+Test")
private val coroutineExemptions = setOf(
    "await-free-space",
    "close-write-channel"
)
private val testExemptions = setOf(
    "ByteChannelConcurrentTest",
    "ByteReadChannelOperationsTest",
)

private val start = System.currentTimeMillis()
private val invocations = ConcurrentHashMap<Invocation, ThreadDetails>().also { addShutdownHook() }

private fun addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread {
        Paths.get(OUTPUT).writer(UTF_8, APPEND, CREATE).buffered().run {
            try {
                val byOperationOnInstance = invocations.keys.groupBy { (instance, operation) ->
                    "$instance-$operation"
                }.filterValues { matches ->
                    matches.size > 1 &&
                        matches.all { it.coroutineName.substringBefore('#') !in coroutineExemptions } &&
                        invocations[matches.first()]!!.testName !in testExemptions
                }
                appendLine("Total invocations:       ${invocations.keys.count()}")
                appendLine("Contentious invocations: ${byOperationOnInstance.values.sumOf { it.size }}")
                appendLine()
                byOperationOnInstance.forEach { (_, matches) ->
                    val testName = invocations[matches.first()]!!.testName
                    appendLine(testName)
                    appendLine("=".repeat(testName.length))
                    for (match in matches) {
                        val threadDetails = invocations[match]!!
                        val (_, operation, coroutineName) = match

                        appendLine("  [$operation] $coroutineName")
                        appendLine("      times: ${threadDetails.timings.joinToString()}")
                        threadDetails.stackTrace.forEach {
                            appendLine("      $it ${it.className}")
                        }
                    }
                    appendLine()
                }
            } finally {
                close()
                println("Invocation report written to ${Paths.get(OUTPUT).toAbsolutePath()}")
                //println(Paths.get(OUTPUT).toFile().readText())
            }
        }
    })
}

private fun Array<StackTraceElement>.isInRunBlocking(): Boolean {
    for (element in this) {
        val stringValue = element.toString()
        if ("launch" in stringValue)
            return false
        if (testRegex in element.className)
            return false
        else if ("runBlocking" in stringValue)
            return true
    }
    return false
}

public actual fun Any.debug(operation: String) {
    val currentThread = Thread.currentThread()
    val coroutineName = currentThread.name.substringAfterLast('@', "unnamed")
    val invocation = Invocation(System.identityHashCode(this), operation, coroutineName)
    val stackTrace = currentThread.stackTrace
    if (!stackTrace.isInRunBlocking()) {
        invocations[invocation] = invocations[invocation]?.addTime() ?: ThreadDetails.of(stackTrace)
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.writer

public data class Invocation(
    val instance: Int,
    val operation: String,
    val coroutineName: String,
)

public data class ThreadDetails(
    val stackTrace: List<StackTraceElement>,
    val testName: String,
) {
    internal companion object {
        internal fun of(stackTrace: Array<StackTraceElement>): ThreadDetails =
            ThreadDetails(
                stackTrace.drop(2).take(20),
                stackTrace.getTestName()
            )
    }
}

private fun Array<StackTraceElement>.getTestName(): String =
    firstNotNullOfOrNull {
        testRegex.find(it.className)?.value
    } ?: "???"

private val testRegex = Regex("\\w+?[A-Z][a-z]+Test")
private val exemptions = setOf(
    "ByteChannelConcurrentTest",
    "ByteReadChannelOperationsTest",
)

private val invocations = ConcurrentHashMap<Invocation, ThreadDetails>().also { invocations ->
    Runtime.getRuntime().addShutdownHook(Thread {
        val outputFile = Paths.get("invocations.txt")
        outputFile.writer().buffered().run {
            try {
                val byOperationOnInstance = invocations.keys.groupBy { (instance, operation) ->
                    "$instance-$operation"
                }.filterValues { matches ->
                    matches.size > 1 && invocations[matches.first()]!!.testName !in exemptions
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
                        val (instance, operation, coroutineName) = match

                        appendLine("  $operation $coroutineName")
                        threadDetails.stackTrace.forEach {
                            appendLine("      $it ${it.className}")
                        }
                    }
                    appendLine()
                }
            } finally {
                close()
                println("Invocation report written to ${outputFile.toAbsolutePath()}")
                println(outputFile.toFile().readText())
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
        invocations[invocation] = ThreadDetails.of(stackTrace)
    }
}

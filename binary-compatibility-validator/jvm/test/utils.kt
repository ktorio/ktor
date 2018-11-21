/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.tools

import java.io.*
import kotlin.test.*

private val OVERWRITE_EXPECTED_OUTPUT =
    System.getProperty("overwrite.output")?.toBoolean() ?: false // use -Doverwrite.output=true

fun List<ClassBinarySignature>.dumpAndCompareWith(to: File) {
    if (!to.exists()) {
        to.parentFile?.mkdirs()
        to.bufferedWriter().use { dump(to = it) }
        fail("Expected data file did not exist. Generated: $to")
    } else {
        val actual = dump(to = StringBuilder())
        assertEqualsToFile(to, actual)
    }
}

private fun assertEqualsToFile(to: File, actual: CharSequence) {
    val actualText = actual.trimTrailingWhitespacesAndAddNewlineAtEOF()
    val expectedText = to.readText().trimTrailingWhitespacesAndAddNewlineAtEOF()
    if (expectedText == actualText) return // Ok
    // Difference
    if (OVERWRITE_EXPECTED_OUTPUT) {
        to.writeText(actualText)
        println("Generated: $to")
        return // make test pass when overwriting output
    }
    // Fail on difference
    assertEquals(
        expectedText,
        actualText,
        "Actual data differs from file content: ${to.name}\nTo overwrite the expected API rerun with -Doverwrite.output=true parameter\n"
    )
}

private fun CharSequence.trimTrailingWhitespacesAndAddNewlineAtEOF(): String =
    this.lineSequence().map { it.trimEnd() }.joinToString(separator = "\n").let {
        if (it.endsWith("\n")) it else it + "\n"
    }


private val UPPER_CASE_CHARS = Regex("[A-Z]+")

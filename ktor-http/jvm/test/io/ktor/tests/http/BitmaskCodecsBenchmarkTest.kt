/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// ============================================================================
// OLD IMPLEMENTATIONS (baseline)
//
// Copied verbatim (only names/visibility adjusted) from:
//   git show HEAD:ktor-http/common/src/io/ktor/http/Codecs.kt
//   git show HEAD:ktor-http/common/src/io/ktor/http/HttpHeaders.kt
//
// These exist purely so this test can A/B-benchmark and cross-check them
// against the current public implementations living in the working tree.
// ============================================================================

private val oldUrlAlphabet = ((('a'..'z') + ('A'..'Z') + ('0'..'9')).map { it.code.toByte() }).toSet()
private val oldUrlAlphabetChars = ((('a'..'z') + ('A'..'Z') + ('0'..'9'))).toSet()
private val oldHexAlphabet = (('a'..'f') + ('A'..'F') + ('0'..'9')).toSet()

/**
 * https://tools.ietf.org/html/rfc3986#section-2
 */
private val oldUrlProtocolPart = setOf(
    ':', '/', '?', '#', '[', ']', '@', // general
    '!', '$', '&', '\'', '(', ')', '*', ',', ';', '=', // sub-components
    '-', '.', '_', '~', '+' // unreserved
).map { it.code.toByte() }

/**
 * from `pchar` in https://tools.ietf.org/html/rfc3986#section-2
 */
private val oldValidPathPart = setOf(
    ':', '@',
    '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
    '-', '.', '_', '~'
)

/**
 * Characters allowed in url according to https://tools.ietf.org/html/rfc3986#section-2.3
 */
private val oldSpecialSymbols = listOf('-', '.', '_', '~').map { it.code.toByte() }

private fun String.oldEncodeURLQueryComponent(
    encodeFull: Boolean = false,
    spaceToPlus: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = buildString {
    val content = charset.newEncoder().encode(this@oldEncodeURLQueryComponent)
    content.oldForEach {
        when {
            it == ' '.code.toByte() -> if (spaceToPlus) append('+') else append("%20")
            it in oldUrlAlphabet || (!encodeFull && it in oldUrlProtocolPart) -> append(it.toInt().toChar())
            else -> append(it.oldPercentEncode())
        }
    }
}

private fun String.oldEncodeURLPathPart(): String = oldEncodeURLPath(encodeSlash = true)

private fun String.oldEncodeURLPath(
    encodeSlash: Boolean = false,
    encodeEncoded: Boolean = true,
): String = buildString {
    val charset = Charsets.UTF_8

    var index = 0
    while (index < this@oldEncodeURLPath.length) {
        val current = this@oldEncodeURLPath[index]
        if ((!encodeSlash && current == '/') || current in oldUrlAlphabetChars || current in oldValidPathPart) {
            append(current)
            index++
            continue
        }

        if (!encodeEncoded &&
            current == '%' &&
            index + 2 < this@oldEncodeURLPath.length &&
            this@oldEncodeURLPath[index + 1] in oldHexAlphabet &&
            this@oldEncodeURLPath[index + 2] in oldHexAlphabet
        ) {
            append(current)
            append(this@oldEncodeURLPath[index + 1])
            append(this@oldEncodeURLPath[index + 2])

            index += 3
            continue
        }

        val symbolSize = if (current.isSurrogate()) 2 else 1
        // we need to call newEncoder() for every symbol, otherwise it won't work
        charset.newEncoder().encode(this@oldEncodeURLPath, index, index + symbolSize).oldForEach {
            append(it.oldPercentEncode())
        }
        index += symbolSize
    }
}

private fun String.oldEncodeURLParameter(
    spaceToPlus: Boolean = false
): String = buildString {
    val content = Charsets.UTF_8.newEncoder().encode(this@oldEncodeURLParameter)
    content.oldForEach {
        when {
            it in oldUrlAlphabet || it in oldSpecialSymbols -> append(it.toInt().toChar())
            spaceToPlus && it == ' '.code.toByte() -> append('+')
            else -> append(it.oldPercentEncode())
        }
    }
}

private fun Byte.oldPercentEncode(): String {
    val code = toInt() and 0xff
    val array = CharArray(3)
    array[0] = '%'
    array[1] = oldHexDigitToChar(code shr 4)
    array[2] = oldHexDigitToChar(code and 0xf)
    return array.concatToString()
}

private fun oldHexDigitToChar(digit: Int): Char = when (digit) {
    in 0..9 -> '0' + digit
    else -> 'A' + digit - 10
}

private fun Source.oldForEach(block: (Byte) -> Unit) {
    takeWhile { buffer ->
        while (buffer.canRead()) {
            block(buffer.readByte())
        }
        true
    }
}

// from HttpHeaders.kt
private fun oldIsDelimiter(ch: Char): Boolean = ch in "\"(),/:;<=>?@[\\]{}"

/**
 * Baseline copy of `HttpHeaders.checkHeaderName`'s body, throwing [IllegalHeaderNameException] identically.
 */
private fun oldCheckHeaderName(name: String) {
    name.forEachIndexed { index, ch ->
        if (ch <= ' ' || oldIsDelimiter(ch)) {
            throw IllegalHeaderNameException(name, index)
        }
    }
}

// ============================================================================
// Corpus shared by the correctness test and (indirectly) sanity-checked by
// the benchmark's realistic workloads.
// ============================================================================

private val asciiPrintableChars: List<Char> = (0x20..0x7E).map { it.toChar() }
private val reservedSampleChars: List<Char> = ":/?#[]@!$&'()*,;=-._~+%".toList()
private val nonAsciiSamples: List<String> = listOf("é", "日", "😀")

private fun buildLongMixedString(): String = buildString {
    repeat(5) {
        append("The quick brown fox/jumps over: lazy dog? #42 ")
        append("café München 日本語 😀 ")
        append("a+b=c&d=e%20f%2Fg ")
    }
}

private fun buildDeterministicCorpus(): List<String> {
    val items = mutableListOf(
        "",
        "/",
        " ",
        "%",
        "%2F",
        "a%2Fb",
        "hello world",
        "a+b c",
        "café",
        "München",
        "日本語",
        "😀emoji",
        ":/?#[]@!\$&'()*,;=-._~+", // URL_PROTOCOL_PART reserved chars, all in one string
        ":@!\$&'()*+,;=-._~", // VALID_PATH_PART reserved chars, all in one string
        "-._~", // SPECIAL_SYMBOLS, all in one string
        buildLongMixedString()
    )
    asciiPrintableChars.forEach { items.add(it.toString()) }
    return items
}

private fun buildRandomCorpus(random: Random, count: Int): List<String> = List(count) {
    val targetLength = random.nextInt(0, 41)
    buildString {
        while (length < targetLength) {
            when (random.nextInt(0, 10)) {
                in 0..5 -> append(asciiPrintableChars[random.nextInt(asciiPrintableChars.size)])
                in 6..8 -> append(reservedSampleChars[random.nextInt(reservedSampleChars.size)])
                else -> append(nonAsciiSamples[random.nextInt(nonAsciiSamples.size)])
            }
        }
    }
}

private fun fullCorpus(): List<String> = buildDeterministicCorpus() + buildRandomCorpus(Random(42), 2000)

class BitmaskCodecsBenchmarkTest {

    // Class-level accumulator: every measured/tested call folds its result into this,
    // and it is printed at the end of the benchmark so the JIT can never treat the
    // encode/validate calls as dead code.
    private var blackhole: Long = 0L

    // ------------------------------------------------------------------
    // 1. Correctness
    // ------------------------------------------------------------------

    @Test
    fun oldAndNewImplementationsAgree() {
        val corpus = fullCorpus()

        for (s in corpus) {
            assertEquals(
                s.oldEncodeURLQueryComponent(),
                s.encodeURLQueryComponent(),
                "encodeURLQueryComponent() mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLQueryComponent(encodeFull = true),
                s.encodeURLQueryComponent(encodeFull = true),
                "encodeURLQueryComponent(encodeFull=true) mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLQueryComponent(spaceToPlus = true),
                s.encodeURLQueryComponent(spaceToPlus = true),
                "encodeURLQueryComponent(spaceToPlus=true) mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLPathPart(),
                s.encodeURLPathPart(),
                "encodeURLPathPart() mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLPath(encodeSlash = false, encodeEncoded = false),
                s.encodeURLPath(encodeSlash = false, encodeEncoded = false),
                "encodeURLPath(encodeSlash=false, encodeEncoded=false) mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLParameter(),
                s.encodeURLParameter(),
                "encodeURLParameter() mismatch for ${s.debugRepr()}"
            )
            assertEquals(
                s.oldEncodeURLParameter(spaceToPlus = true),
                s.encodeURLParameter(spaceToPlus = true),
                "encodeURLParameter(spaceToPlus=true) mismatch for ${s.debugRepr()}"
            )

            val (oldThrew, oldPosition) = captureHeaderNameCheck(::oldCheckHeaderName, s)
            val (newThrew, newPosition) = captureHeaderNameCheck(HttpHeaders::checkHeaderName, s)
            assertEquals(oldThrew, newThrew, "checkHeaderName() throw-vs-no-throw mismatch for ${s.debugRepr()}")
            if (oldThrew && newThrew) {
                assertEquals(oldPosition, newPosition, "checkHeaderName() position mismatch for ${s.debugRepr()}")
            }
        }
    }

    private fun captureHeaderNameCheck(check: (String) -> Unit, value: String): Pair<Boolean, Int?> = try {
        check(value)
        false to null
    } catch (e: IllegalHeaderNameException) {
        true to e.position
    }

    private fun String.debugRepr(): String =
        "\"" + map { c -> if (c.code in 0x20..0x7E) c.toString() else "\\u%04x".format(c.code) }.joinToString("") + "\""

    // ------------------------------------------------------------------
    // 2. Benchmark (hand-rolled, JMH-style discipline, same-JVM A/B only)
    // ------------------------------------------------------------------

    private data class TimingResult(val medianOldNsPerOp: Double, val medianNewNsPerOp: Double) {
        val speedup: Double get() = medianOldNsPerOp / medianNewNsPerOp
    }

    private fun warmupRound(workload: List<String>, op: (String) -> Long, minOps: Int = 50_000) {
        var index = 0
        var count = 0
        while (count < minOps) {
            blackhole += op(workload[index % workload.size])
            index++
            count++
        }
    }

    private fun timedSegment(workload: List<String>, op: (String) -> Long, iterations: Int): Long {
        val start = System.nanoTime()
        for (n in 0 until iterations) {
            for (s in workload) {
                blackhole += op(s)
            }
        }
        return System.nanoTime() - start
    }

    private fun calibrateIterations(
        workload: List<String>,
        op: (String) -> Long,
        targetNanos: Long = 100_000_000L
    ): Int {
        var n = 64
        while (true) {
            val elapsed = timedSegment(workload, op, n)
            if (elapsed >= targetNanos || n >= 5_000_000) return n
            n *= 2
        }
    }

    private fun median(values: DoubleArray): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun measureOperation(
        workload: List<String>,
        oldOp: (String) -> Long,
        newOp: (String) -> Long
    ): TimingResult {
        // Warmup: 5 rounds of BOTH old and new, each round >= 50_000 op-batches over the workload.
        repeat(5) {
            warmupRound(workload, oldOp)
            warmupRound(workload, newOp)
        }

        // Calibrate a shared iteration count N so that a segment for either impl takes >= ~100ms.
        val nOld = calibrateIterations(workload, oldOp)
        val nNew = calibrateIterations(workload, newOp)
        val n = maxOf(nOld, nNew)
        val opsPerSegment = (n.toLong() * workload.size).toDouble()

        val oldNsPerOp = DoubleArray(10)
        val newNsPerOp = DoubleArray(10)

        // Measurement: 10 rounds, alternating old/new within each round.
        repeat(10) { round ->
            val oldElapsed = timedSegment(workload, oldOp, n)
            val newElapsed = timedSegment(workload, newOp, n)
            oldNsPerOp[round] = oldElapsed / opsPerSegment
            newNsPerOp[round] = newElapsed / opsPerSegment
        }

        return TimingResult(median(oldNsPerOp), median(newNsPerOp))
    }

    private fun measureAllocationBytesPerOp(
        workload: List<String>,
        op: (String) -> Long,
        batch: Int = 100_000
    ): Double? {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return null
        if (!bean.isThreadAllocatedMemorySupported) return null
        if (!bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
        @Suppress("DEPRECATION")
        val threadId = Thread.currentThread().id

        var index = 0
        val before = bean.getThreadAllocatedBytes(threadId)
        var count = 0
        while (count < batch) {
            blackhole += op(workload[index % workload.size])
            index++
            count++
        }
        val after = bean.getThreadAllocatedBytes(threadId)
        return (after - before).toDouble() / batch
    }

    @Test
    fun benchmark() {
        val paths = listOf(
            "api",
            "users",
            "v2",
            "550e8400-e29b-41d4-a716-446655440000",
            "orders-2026-08",
            "file name.txt",
            "hello world",
            "café-menu"
        )
        val queries = listOf(
            "kotlin coroutines guide",
            "user@example.com",
            "price>100&x=1",
            "plain",
            "a b+c",
            "München weather"
        )
        val headerNames = listOf(
            "Content-Type",
            "Content-Length",
            "Authorization",
            "X-Request-Id",
            "Accept-Encoding",
            "User-Agent",
            "Cache-Control",
            "X-Correlation-Id"
        )

        val oldPathPartOp: (String) -> Long = { s -> s.oldEncodeURLPathPart().length.toLong() }
        val newPathPartOp: (String) -> Long = { s -> s.encodeURLPathPart().length.toLong() }

        val oldQueryOp: (String) -> Long = { s -> s.oldEncodeURLQueryComponent().length.toLong() }
        val newQueryOp: (String) -> Long = { s -> s.encodeURLQueryComponent().length.toLong() }

        val oldParamOp: (String) -> Long = { s -> s.oldEncodeURLParameter(spaceToPlus = true).length.toLong() }
        val newParamOp: (String) -> Long = { s -> s.encodeURLParameter(spaceToPlus = true).length.toLong() }

        val oldHeaderOp: (String) -> Long = { s ->
            oldCheckHeaderName(s)
            s.length.toLong()
        }
        val newHeaderOp: (String) -> Long = { s ->
            HttpHeaders.checkHeaderName(s)
            s.length.toLong()
        }

        val timings = linkedMapOf(
            "pathPart" to measureOperation(paths, oldPathPartOp, newPathPartOp),
            "queryComponent" to measureOperation(queries, oldQueryOp, newQueryOp),
            "urlParameter" to measureOperation(queries, oldParamOp, newParamOp),
            "checkHeaderName" to measureOperation(headerNames, oldHeaderOp, newHeaderOp)
        )

        val allocations = linkedMapOf(
            "pathPart" to (
                measureAllocationBytesPerOp(paths, oldPathPartOp) to
                    measureAllocationBytesPerOp(paths, newPathPartOp)
                ),
            "queryComponent" to (
                measureAllocationBytesPerOp(queries, oldQueryOp) to
                    measureAllocationBytesPerOp(queries, newQueryOp)
                ),
            "urlParameter" to (
                measureAllocationBytesPerOp(queries, oldParamOp) to
                    measureAllocationBytesPerOp(queries, newParamOp)
                ),
            "checkHeaderName" to (
                measureAllocationBytesPerOp(headerNames, oldHeaderOp) to
                    measureAllocationBytesPerOp(headerNames, newHeaderOp)
                )
        )

        val report = buildReport(timings, allocations)
        println(report)
        println("blackhole=$blackhole")

        val outputFile = File("build/bitmask-benchmark-results.txt")
        outputFile.parentFile?.mkdirs()
        outputFile.appendText(report)
    }

    private fun buildReport(
        timings: Map<String, TimingResult>,
        allocations: Map<String, Pair<Double?, Double?>>
    ): String = buildString {
        appendLine()
        appendLine("=== BitmaskCodecsBenchmarkTest (${java.time.LocalDateTime.now()}) ===")
        appendLine(
            "NOTE: this is a same-JVM, in-process A/B micro-harness (not JMH) -- " +
                "no fork isolation, treat numbers as indicative, not authoritative."
        )
        appendLine()
        appendLine(String.format("%-18s %14s %14s %10s", "operation", "old ns/op", "new ns/op", "speedup"))
        appendLine("-".repeat(60))
        for ((name, r) in timings) {
            appendLine(
                String.format(
                    "%-18s %14.1f %14.1f %9.2fx",
                    name,
                    r.medianOldNsPerOp,
                    r.medianNewNsPerOp,
                    r.speedup
                )
            )
        }
        appendLine()
        appendLine(String.format("%-18s %16s %16s", "operation", "old bytes/op", "new bytes/op"))
        appendLine("-".repeat(52))
        for ((name, pair) in allocations) {
            val (oldBytes, newBytes) = pair
            appendLine(
                String.format(
                    "%-18s %16s %16s",
                    name,
                    oldBytes?.let { String.format("%.1f", it) } ?: "n/a",
                    newBytes?.let { String.format("%.1f", it) } ?: "n/a"
                )
            )
        }
        appendLine()
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.compat

import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import java.text.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

fun parseLog4jFormat(pattern: String, config: LoggingConfigBuilder): Appendable.(LogRecord) -> Unit {
    return parseLog4jFormat(pattern, config) { GMTDate() }
}

internal fun parseLog4jFormat(
    pattern: String,
    config: LoggingConfigBuilder,
    clock: () -> GMTDate
): Appendable.(LogRecord) -> Unit {
    val chains = parse(pattern, config, clock)
    if (chains.isEmpty()) {
        return {}
    }

    return { record ->
        chains.forEach { chain ->
            chain.append(this, record)
        }
    }
}

private fun parse(pattern: String, config: LoggingConfigBuilder, clock: () -> GMTDate): List<Chain> {
    var start = 0
    val chain = ArrayList<Chain>()

    while (start < pattern.length) {
        val patternStart = pattern.indexOf('%', start)

        if (patternStart != start) {
            val text = when (patternStart) {
                -1 -> pattern.substring(start)
                else -> pattern.substring(start, patternStart)
            }

            chain.add(Chain.Constant(text))
        }

        if (patternStart == -1) {
            break
        }

        if (patternStart == pattern.lastIndex) {
            chain.add(Chain.Constant("%"))
            break
        }

        start = parsePattern(pattern, patternStart, patternStart + 1, chain, config, clock)
    }

    return chain.dropLastWhile { it is Chain.LineFeed }
}

private val PATTERN_TOKENS = listOf(
    "d", "date", "c", "C", "logger", "enc",
    "encode", "equals", "equalsIgnoreCase",
    "ex", "exception", "throwable", "F", "file", "highlight", "K", "map", "MAP",
    "l", "location", "L", "line", "m", "msg", "message", "level", "p",
    "M", "method", "marker", "markerSimpleName", "maxLen", "maxLength",
    "n", "N", "nano", "pid", "processId",
    "r", "relative", "sn", "sequenceNumber", "T", "tid", "thread", "t", "tn", "threadName", "thread",
    "tp", "threadPriority", "fqcn", "X", "MDC", "mdc", "u", "uuid", "%", "-"
).sortedByDescending { it.length }

private fun parsePattern(
    pattern: String,
    patternStart: Int,
    start: Int,
    chain: MutableList<Chain>,
    config: LoggingConfigBuilder,
    clock: () -> GMTDate
): Int {
    require(pattern[patternStart] == '%')
    require(patternStart + 1 < pattern.length)
    require(start > patternStart)

    val patternEnd = parseToken(pattern, PATTERN_TOKENS, start)
    val patternPart = pattern.substring(start, patternEnd)

    when (patternPart) {
        "d", "date" -> {
            config.ensureLogTime(clock)
            return parseDateFormat(pattern, patternEnd, chain)
        }
        "t", "tn", "thread", "threadName" -> {
            config.ensureThreadName()
            chain.add(Chain.ThreadName)
            return patternEnd
        }
        "-" -> {
            var end = pattern.length
            for (index in start + 1..pattern.lastIndex) {
                if (!pattern[index].isDigit()) {
                    end = index
                    break
                }
            }

            if (end == start + 1) {
                return parsePattern(pattern, patternStart, start + 1, chain, config, clock)
            }

            val sizeLimit = pattern.substring(start, end).toInt()
            val child = ArrayList<Chain>(1)
            val subEnd = parsePattern(pattern, patternStart, end, child, config, clock)
            check(child.size == 1)
            chain.add(Chain.TruncatedDelegate.End(sizeLimit.absoluteValue, sizeLimit.absoluteValue, child[0]))

            return subEnd
        }
        "%" -> {
            chain.add(Chain.Constant("%"))
            return patternEnd
        }
        "c", "logger" -> {
            chain.add(Chain.LoggerName)
            return checkParseMaxLengthSuffix(pattern, patternEnd, chain)
        }
        "fqcn" -> {
            chain.add(Chain.LoggerName)
            return patternEnd
        }
        "level" -> {
            // TODO level parameters
            chain.add(Chain.Level)
            return patternEnd
        }
        "m", "msg", "message" -> {
            // TODO parameters
            chain.add(Chain.Message)
            return patternEnd
        }
        "n" -> {
            chain.add(Chain.LineFeed)
            return start + 1
        }
        "ex", "exception", "throwable" -> {
            // TODO exception options
            chain.add(Chain.Exception)
            return patternEnd
        }
        else -> error("Pattern $patternPart is not supported.")
    }
}

private fun checkParseMaxLengthSuffix(pattern: String, start: Int, chain: MutableList<Chain>): Int {
    if (start < pattern.length && pattern[start] == '{') {
        val end = pattern.indexOf('}', start)
        if (end == -1) {
            error("Unclosed precision specifier: ${pattern.substring(start)}")
        }
        val precisionSpecifier = pattern.substring(start + 1, end)
        chain[chain.lastIndex] = createPrecisionChain(precisionSpecifier, chain[chain.lastIndex])
        return end + 1
    }

    return start
}

private fun createPrecisionChain(pattern: String, chain: Chain): Chain {
    val length = pattern.toIntOrNull()
    return when {
        length == null -> {
            val parts = pattern.split('.')
            if (parts.size == 2 && parts[1] == "" && parts[0].toIntOrNull() != null) {
                Chain.PrecisionTruncatedDelegate.PackageComponentsShortened(parts[0].toInt(), chain)
            } else {
                val patternNode = parts.map {
                    val size = it.toIntOrNull()
                    when {
                        size != null -> Chain.PrecisionTruncatedDelegate.ComplexPattern.PrecisionPatternNode.Truncated(
                            size
                        )
                        else -> Chain.PrecisionTruncatedDelegate.ComplexPattern.PrecisionPatternNode.Replacement(it)
                    }
                }

                Chain.PrecisionTruncatedDelegate.ComplexPattern(patternNode, chain)
            }
        }
        length > 0 -> Chain.PrecisionTruncatedDelegate.Simple(length, chain)
        length < 0 -> Chain.PrecisionTruncatedDelegate.Negative(-length, chain)
        length == 0 -> chain
        else -> error("Unsupported precision $length")
    }
}

private fun parseToken(pattern: String, tokens: List<String>, start: Int): Int {
    if (start < pattern.length) {
        tokens.forEach {
            if (pattern.startsWith(it, start)) {
                return start + it.length
            }
        }
    }

    error("Unsupported token ${guessToken(pattern, start)}")
}

/**
 * NOTE: This is for debug and logging only!
 */
private fun guessToken(pattern: String, start: Int): String {
    return pattern.substring(start).substringBefore('{').substringBefore(' ')
}

private fun parseDateFormat(pattern: String, start: Int, chain: MutableList<Chain>): Int {
    if (start >= pattern.length || pattern[start] != '{') {
        chain.add(Chain.Date())
        return start
    } else {
        val dateFormatEnd = pattern.indexOf('}', start + 1)
        if (dateFormatEnd == -1) {
            error("Date format is incomplete: ${pattern.substring(start)}")
        }
        val dateFormat = pattern.substring(start + 1, dateFormatEnd)

        // verify format
        check(SimpleDateFormat(dateFormat).format(Date()) != null)

        chain.add(Chain.Date(dateFormat))
        return dateFormatEnd + 1
    }
}

private sealed class Chain {
    abstract fun append(destination: Appendable, record: LogRecord)

    class Constant(val text: String) : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(text)
        }
    }

    class Date(val dateFormat: String = "yyyy-MM-dd HH:mm:ss,SSS") : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(SimpleDateFormat(dateFormat).format(record.logTime?.toJvmDate() ?: return))
        }
    }

    object ThreadName : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(record.threadName)
        }
    }

    sealed class PrecisionTruncatedDelegate(val delegate: Chain) : Chain() {
        class Simple(val precision: Int, delegate: Chain) : PrecisionTruncatedDelegate(delegate) {
            init {
                require(precision > 0)
            }

            override fun append(destination: Appendable, record: LogRecord) {
                val text = buildString {
                    delegate.append(this, record)

                    var startIndex = 0
                    var count = 0
                    for (index in lastIndex downTo 0) {
                        if (this[index] == '.') {
                            count++
                            if (count >= precision) {
                                startIndex = index + 1
                                break
                            }
                        }
                    }

                    if (startIndex > 0) {
                        delete(0, startIndex)
                    }
                }

                destination.append(text)
            }
        }

        class Negative(val precision: Int, delegate: Chain) : PrecisionTruncatedDelegate(delegate) {
            init {
                require(precision > 0)
            }

            override fun append(destination: Appendable, record: LogRecord) {
                val text = buildString {
                    delegate.append(this, record)

                    var startIndex = 0
                    var count = 0
                    for (index in 0..lastIndex) {
                        if (this[index] == '.') {
                            count++
                            if (count >= precision) {
                                startIndex = index + 1
                                break
                            }
                        }
                    }

                    if (startIndex > 0) {
                        delete(0, startIndex)
                    }
                }

                destination.append(text)
            }
        }

        class PackageComponentsShortened(val componentLength: Int, delegate: Chain) :
            PrecisionTruncatedDelegate(delegate) {
            override fun append(destination: Appendable, record: LogRecord) {
                val components = buildString {
                    delegate.append(this, record)
                }.split('.')

                destination.append(buildString {
                    for (index in 0 until components.lastIndex) {
                        append(components[index].take(componentLength))
                        append('.')
                    }

                    append(components.last())
                })
            }
        }

        class ComplexPattern(val pattern: List<PrecisionPatternNode>, delegate: Chain) :
            PrecisionTruncatedDelegate(delegate) {

            override fun append(destination: Appendable, record: LogRecord) {
                val components = buildString {
                    delegate.append(this, record)
                }.split('.')

                destination.append(buildString {
                    for (index in 0 until components.lastIndex) {
                        val component = components[index]

                        when (val patternNode = pattern.getOrNull(index) ?: pattern.last()) {
                            is PrecisionPatternNode.Truncated -> append(component.take(patternNode.size))
                            is PrecisionPatternNode.Replacement -> append(patternNode.text)
                        }

                        append('.')
                    }

                    append(components.last())
                })
            }

            sealed class PrecisionPatternNode {
                class Truncated(val size: Int) : PrecisionPatternNode()
                class Replacement(val text: String) : PrecisionPatternNode()
            }
        }
    }

    sealed class TruncatedDelegate(val minSize: Int, val maxSize: Int, val delegate: Chain) : Chain() {
        protected abstract fun truncate(builder: StringBuilder, size: Int)

        override fun append(destination: Appendable, record: LogRecord) {
            val text = buildString {
                delegate.append(this, record)

                while (length < minSize) {
                    append(' ')
                }
                if (length > maxSize) {
                    truncate(this, length - maxSize)
                }
            }

            destination.append(text)
        }

        class End(minSize: Int, maxSize: Int, delegate: Chain) :
            TruncatedDelegate(minSize, maxSize, delegate) {

            override fun truncate(builder: StringBuilder, size: Int) {
                builder.delete(builder.length - size, builder.length)
            }
        }
    }

    object Message : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(record.text)
        }
    }

    object Level : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(record.level.name)
        }
    }

    object LoggerName : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(record.name ?: "")
        }
    }

    object LineFeed : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append("\n")
        }
    }

    object Exception : Chain() {
        override fun append(destination: Appendable, record: LogRecord) {
            destination.append(record.exception?.toString() ?: "")
        }
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*

internal class FilePathPattern(val parts: List<Component>) {
    constructor(pattern: String) : this(parse(pattern))

    private val estimate: Int = parts.sumBy {
        when (it) {
            Component.Separator -> 1
            Component.Number -> Int.MAX_VALUE.toString().length
            is Component.Date -> it.format.estimate
            is Component.ConstantPart -> it.text.length
        }
    }

    init {
        require(parts.any { it !is Component.Separator }) { "File name pattern shouldn't be empty" }
        require(parts.any { it is Component.Number }) { "It should be at least one number pattern %i" }
        require(parts.last() != Component.Separator) { "File name shouldn't be empty: path ends with separator: $parts" }
    }

    /**
     * Produces regular expression for each path component
     */
    internal val pathComponentsPatterns: List<PatternOrConstant> =
        parts.split(Component.Separator).mapIndexed { index, pathComponentParts ->
            require(index == 0 || pathComponentParts.isNotEmpty()) { "Path component shouldn't be empty: two path separators in a row." }

            when {
                pathComponentParts.size == 1 && pathComponentParts[0] is Component.ConstantPart -> {
                    PatternOrConstant.Constant(
                        (pathComponentParts[0] as Component.ConstantPart).text,
                        pathComponentParts
                    )
                }
                else -> buildString {
                    for (part in pathComponentParts) {
                        append('(')
                        when (part) {
                            Component.Separator -> error("parts.split produced wrong sequence: $pathComponentParts")
                            Component.Number -> append("[0-9]+")
                            is Component.Date -> append(part.format.toRegex().pattern)
                            is Component.ConstantPart -> append(Regex.escape(part.text))
                        }
                        append(')')
                    }
                }.toRegex(RegexOption.IGNORE_CASE).let { PatternOrConstant.Pattern(it, pathComponentParts) }
            }
        }

    fun format(date: GMTDate, number: Int): String = buildString(estimate) {
        for (part in parts) {
            when (part) {
                Component.Separator -> append('/')
                Component.Number -> append(number.toString())
                is Component.Date -> append(date.format(part.format))
                is Component.ConstantPart -> append(part.text)
            }
        }
    }

    private fun <T> List<T>.split(delimiter: T): List<List<T>> {
        if (isEmpty()) return emptyList()

        val result = ArrayList<ArrayList<T>>(1)
        var last = ArrayList<T>()
        result.add(last)

        forEach { element ->
            when (element) {
                delimiter -> {
                    last = ArrayList()
                    result.add(last)
                }
                else -> {
                    last.add(element)
                }
            }
        }

        return result
    }

    fun matches(filePath: String): Boolean {
        val pathComponents = filePath.split("/")
        if (pathComponents.size != pathComponentsPatterns.size) return false

        pathComponentsPatterns.forEachIndexed { index, part ->
            when (part) {
                is PatternOrConstant.Pattern -> {
                    if (!part.regex.matches(pathComponents[index])) {
                        return false
                    }
                }
                is PatternOrConstant.Constant -> {
                    if (part.text != pathComponents[index]) {
                        return false
                    }
                }
            }
        }

        return true
    }

    sealed class Component {
        object Separator : Component()
        object Number : Component()
        data class Date(val format: StringPatternDateFormat) : Component()
        data class ConstantPart(val text: String) : Component() {
            init {
                require(text.isNotEmpty()) { "Constant part shouldn't be empty" }
                require('/' !in text) { "Path separators couldn't be in a static part: $text" }
                require('\\' !in text) { "Path separators couldn't be in a static part: $text" }
            }
        }
    }

    companion object {
        private fun parse(pattern: String): List<Component> {
            var startIndex = 0
            val result = ArrayList<Component>()

            while (startIndex < pattern.length) {
                val percentIndex = pattern.indexOfAny(charArrayOf('%', '/'), startIndex)
                when {
                    percentIndex == -1 -> {
                        result.addConstantIfNotEmpty(pattern, startIndex)
                        startIndex = pattern.length
                    }
                    pattern[percentIndex] == '/' -> {
                        result.addConstantIfNotEmpty(pattern, startIndex, percentIndex)
                        if (result.lastOrNull() != Component.Separator) {
                            result += Component.Separator
                        }
                        startIndex = percentIndex + 1
                    }
                    else -> {
                        result.addConstantIfNotEmpty(pattern, startIndex, percentIndex)

                        if (percentIndex == pattern.lastIndex) {
                            error("Trailing % in file path pattern is not allowed")
                        }
                        when (pattern[percentIndex + 1]) {
                            'i', 'n' -> {
                                result += Component.Number
                                startIndex = percentIndex + 2
                            }
                            'd' -> {
                                val datePattern =
                                    when {
                                        percentIndex + 2 < pattern.length && pattern[percentIndex + 2] == '{' -> {
                                            val endIndex = pattern.indexOf('}', percentIndex + 2)
                                            if (endIndex == -1) {
                                                error("Unclosed date pattern: ${pattern.substring(percentIndex)}")
                                            }
                                            startIndex = endIndex + 1
                                            pattern.substring(percentIndex + 3, endIndex)
                                        }
                                        else -> {
                                            startIndex = percentIndex + 2
                                            "yyyy-MM-dd"
                                        }
                                    }
                                result += Component.Date(StringPatternDateFormat(datePattern))
                            }
                            else -> error("Unsupported path pattern ${pattern[percentIndex + 1]}")
                        }
                    }
                }
            }

            return result
        }

        private fun MutableList<Component>.addConstantIfNotEmpty(
            pattern: String,
            startIndex: Int,
            endIndex: Int = pattern.length
        ) {
            pattern.substring(startIndex, endIndex).takeIf { it.isNotEmpty() }?.let { constant ->
                val last = lastOrNull() as? Component.ConstantPart
                when {
                    last != null -> set(lastIndex, Component.ConstantPart(last.text + constant))
                    else -> add(Component.ConstantPart(constant))
                }
            }
        }
    }

    internal sealed class PatternOrConstant(val relatedComponents: List<Component>) {
        class Pattern(val regex: Regex, relatedComponents: List<Component>) : PatternOrConstant(relatedComponents)
        class Constant(val text: String, relatedComponents: List<Component>) : PatternOrConstant(relatedComponents)
    }
}

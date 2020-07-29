/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*

@InternalAPI
public class LocationPattern private constructor(private val segments: List<Segment>) {
    public constructor(pattern: String) : this(RoutingPath.parse(pattern))
    private constructor(routing: RoutingPath) : this(routing.parts.map { segment ->
        Segment.fromRouting(segment)
    })

    private val sizeEstimate: Int = segments.sumBy { it.estimate() } + segments.size + 1

    public val pathParameterNames: Set<String> = segments.filterIsInstance<Segment.VariableSubstitution>()
        .map { it.name }
        .toSet()

    @OptIn(ExperimentalStdlibApi::class)
    public fun format(pathParameters: Parameters): String = buildString(sizeEstimate) {
        val indexes = mutableMapOf<String, Int>()

        segments.forEach { segment ->
            append('/')
            when (segment) {
                is Segment.Constant -> append(segment.text.encodeURLPathComponent())
                is Segment.VariableSubstitution -> {
                    val index = indexes.getOrElse(segment.name) { 0 }
                    indexes[segment.name] = index + 1
                    val allValues = pathParameters.getAll(segment.name).orEmpty()

                    if (allValues.isEmpty()) {
                        if (segment.ellipsis || segment.optional) {
                            deleteAt(lastIndex)
                        } else {
                            throw IllegalArgumentException("No parameter ${segment.name} specified")
                        }
                    } else {
                        append(segment.prefix.encodeURLPathComponent())
                        if (segment.ellipsis) {
                            allValues.joinTo(this, "/") { it.encodeURLPathComponent() }
                        } else {
                            val value = allValues.getOrElse(index) { allValues.last() }
                            append(value.encodeURLPathComponent())
                        }
                        append(segment.suffix.encodeURLPathComponent())
                    }
                }
            }
        }

        if (isEmpty()) {
            append('/')
        }
    }

    public fun parse(path: String): Parameters = Parameters.build {
        check(path.startsWith("/"))

        var index = 1
        segments.forEachIndexed { segmentIndex, segment ->
            when (segment) {
                is Segment.Constant -> {
                    check(path.startsWith(segment.text, startIndex = index)) {
                        "Expected a constant path segment '${segment.text}'"
                    }
                    index += segment.text.length
                }
                is Segment.VariableSubstitution -> {
                    index = parseSegment(path, index, segment, this)
                }
            }

            if (index != path.length) {
                check(path[index] == '/')
                index++
            } else {
                check(segmentIndex == segments.lastIndex)
            }
        }

        if (index < path.length) {
            error("Extra path segments: ${path.substring(index)}")
        }
    }

    public operator fun plus(other: LocationPattern): LocationPattern = LocationPattern(segments + other.segments)

    private fun parseSegment(
        path: String,
        startIndex: Int,
        segment: Segment.VariableSubstitution,
        builder: ParametersBuilder
    ): Int {
        var index = startIndex

        index += segment.prefix.length

        do {
            val nextSlashIndex = path.indexOf('/', index)
            val value: String

            if (nextSlashIndex == -1) {
                value = path.substring(index).removeSuffix(segment.suffix)
                index = path.length
            } else {
                val part = path.substring(index, nextSlashIndex)
                value = when {
                    segment.ellipsis -> part
                    else -> part.removeSuffix(segment.suffix)
                }
                index = nextSlashIndex
            }

            builder.append(segment.name, value.decodeURLPart())

            if (index == path.length || !segment.ellipsis) {
                break
            }

            check(path[index] == '/')
            index++
        } while (true)

        return index
    }

    private sealed class Segment {
        abstract fun estimate(): Int

        class Constant(val text: String) : Segment() {
            override fun estimate(): Int = text.length

            override fun toString(): String = "Constant($text)"
        }

        class VariableSubstitution(
            val name: String, val ellipsis: Boolean, val prefix: String, val suffix: String, val optional: Boolean
        ) : Segment() {
            override fun estimate(): Int = prefix.length + suffix.length + 10
            override fun toString(): String = "Substitution($name)"
        }

        companion object {
            fun fromRouting(segment: RoutingPathSegment): Segment {
                return when (segment.kind) {
                    RoutingPathSegmentKind.Constant -> Constant(segment.value)
                    RoutingPathSegmentKind.Parameter -> parseParameter(segment.value)
                }
            }

            private fun parseParameter(value: String): VariableSubstitution {
                val substitutionStart = value.indexOf('{')
                val substitutionEnd = value.indexOf('}')

                if (substitutionStart == -1) {
                    error("No substitution found in pattern $value")
                }
                if (substitutionEnd == -1) {
                    error("Substitution is not closed in pattern $value")
                }

                if (value.lastIndexOf('{') != substitutionStart
                    || value.lastIndexOf('}') != substitutionEnd
                ) {
                    error("Multiple substitutions in a component is not supported: $value")
                }

                val prefix = value.take(substitutionStart)
                val suffix = value.takeLast(value.length - substitutionEnd - 1)

                val substitution = value.substring(substitutionStart + 1, substitutionEnd).trim()
                val ellipsis = substitution.endsWith("...")
                val optional = substitution.endsWith("?")

                val parameterName = substitution.removeSuffix("...").removeSuffix("?").trimEnd()

                if (parameterName.isEmpty()) {
                    error("Unnamed parameters are not supported in locations: '$substitution' in $value")
                }
                if (parameterName.endsWith("?") || parameterName.endsWith("...")) {
                    error("Illegal parameter name '$parameterName'")
                }

                return VariableSubstitution(parameterName, ellipsis, prefix, suffix, optional)
            }
        }
    }
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.routing

import io.ktor.http.*

/**
 * Represents a parsed routing path. Consist of number of segments [parts]
 *
 * @property parts contains parsed routing path segments
 */
public class RoutingPath private constructor(public val parts: List<RoutingPathSegment>) {
    public companion object {
        /**
         * A constant for root routing path
         */
        public val root: RoutingPath = RoutingPath(listOf())

        /**
         * Parse the specified [path] and create an instance of [RoutingPath].
         * It handles wildcards and decodes escape characters properly.
         */
        public fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val segments = path.splitToSequence("/").filter { it.isNotEmpty() }.map { segment ->
                when {
                    segment.contains('{') && segment.contains('}') -> RoutingPathSegment(
                        segment,
                        RoutingPathSegmentKind.Parameter
                    )
                    else -> RoutingPathSegment(segment.decodeURLPart(), RoutingPathSegmentKind.Constant)
                }
            }

            return RoutingPath(segments.toList())
        }
    }

    override fun toString(): String = parts.joinToString("/") { it.value }
}

/**
 * Represent a single routing path segment
 * @property value - segment text value
 * @property kind - segment kind (constant or parameter)
 */
public data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind)

/**
 * Possible routing path segment kinds
 */
public enum class RoutingPathSegmentKind {
    /**
     * Corresponds to constant path segment
     */
    Constant,

    /**
     * Corresponds to a parameter path segment (wildcard or named parameter or both)
     */
    Parameter
}

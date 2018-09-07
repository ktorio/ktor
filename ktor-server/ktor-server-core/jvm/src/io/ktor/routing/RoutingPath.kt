package io.ktor.routing

import io.ktor.http.*

/**
 * Represents a parsed routing path. Consist of number of segments [parts]
 *
 * @property parts contains parsed routing path segments
 */
class RoutingPath private constructor(val parts: List<RoutingPathSegment>) {
    companion object {
        /**
         * A constant for root routing path
         */
        val root: RoutingPath = RoutingPath(listOf())

        /**
         * Parse the specified [path] and create and instance of [RoutingPath].
         * It handles wildcards and decodes escape characters properly.
         */
        fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val segments = path.splitToSequence("/").filter { it.isNotEmpty() }.map { segment ->
                when {
                    segment.contains('{') && segment.contains('}') -> RoutingPathSegment(segment, RoutingPathSegmentKind.Parameter)
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
data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind)

/**
 * Possible routing path segment kinds
 */
enum class RoutingPathSegmentKind {
    /**
     * Corresponds to constant path segment
     */
    Constant,

    /**
     * Corresponds to a parameter path segment (wildcard or named parameter or both)
     */
    Parameter
}


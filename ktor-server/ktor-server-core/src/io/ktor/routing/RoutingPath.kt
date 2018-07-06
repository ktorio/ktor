package io.ktor.routing

import io.ktor.http.*

class RoutingPath private constructor(val parts: List<RoutingPathSegment>) {
    companion object {
        val root: RoutingPath = RoutingPath(listOf())
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

data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind)

enum class RoutingPathSegmentKind {
    Constant, Parameter
}


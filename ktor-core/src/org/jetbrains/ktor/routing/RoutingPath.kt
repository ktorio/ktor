package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*

class RoutingPath private constructor(val parts: List<RoutingPathSegment>) {
    companion object {
        val root: RoutingPath = RoutingPath(listOf())
        fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val segments = path.splitToSequence("/").filter { it.length > 0 }.map {
                when {
                    it == "*" -> RoutingPathSegment("", RoutingPathSegmentKind.Constant, true)
                    it.startsWith("{") && it.endsWith("}") -> {
                        val signature = it.removeSurrounding("{", "}")
                        when {
                            signature.endsWith("?") -> RoutingPathSegment(signature.dropLast(1), RoutingPathSegmentKind.Parameter, true)
                            signature.endsWith("...") -> RoutingPathSegment(signature.dropLast(3), RoutingPathSegmentKind.TailCard, true)
                            else -> RoutingPathSegment(signature, RoutingPathSegmentKind.Parameter, false)
                        }
                    }
                    else -> RoutingPathSegment(decodeURLPart(it), RoutingPathSegmentKind.Constant, false)
                }
            }

            return RoutingPath(segments.toList())
        }
    }

    override fun toString(): String = parts.map { it.value }.joinToString("/")
}

data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind, val optional: Boolean)

enum class RoutingPathSegmentKind {
    Constant, Parameter, TailCard
}


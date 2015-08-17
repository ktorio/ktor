package org.jetbrains.ktor.routing

class RoutingPath {
    public val parts: List<RoutingPathSegment>

    companion object {
        val root: RoutingPath = RoutingPath(listOf())
        fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val splitted = path.split("/").filter { it.length() > 0 }
            val segments = splitted.map {
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
                    else -> RoutingPathSegment(it, RoutingPathSegmentKind.Constant, false)
                }
            }

            return RoutingPath(segments)
        }
    }

    private constructor(segments: List<RoutingPathSegment>) {
        this.parts = segments
    }

    public fun combine(path: RoutingPath): RoutingPath {
        return RoutingPath(parts + path.parts)
    }

    override fun toString(): String {
        return parts.map { it.value }.join("/")
    }
}

data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind, val optional: Boolean)

enum class RoutingPathSegmentKind {
    Constant, Parameter, TailCard
}


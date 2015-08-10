package org.jetbrains.ktor.routing

class RoutingPath  {
    public val parts: List<RoutingPathPart>

    companion object {
        fun parse(path: String): RoutingPath {
            val parts = if (path == "/")
                listOf()
            else
                path.split("/").filter { it.length() > 0 }.map {
                    when {
                        it.startsWith("**") -> RoutingPathPart(it.drop(2), RoutingPathPartKind.TailCard, true)
                        it.startsWith("*") -> RoutingPathPart(it.drop(1), RoutingPathPartKind.Constant, true)
                        it.startsWith(":?") -> RoutingPathPart(it.drop(2), RoutingPathPartKind.Parameter, true)
                        it.startsWith(":") -> RoutingPathPart(it.drop(1), RoutingPathPartKind.Parameter, false)
                        else -> RoutingPathPart(it, RoutingPathPartKind.Constant, false)
                    }
                }

            return RoutingPath(parts)
        }
    }

    private constructor(parts: List<RoutingPathPart>) {
        this.parts = parts
    }

    public fun combine(path: RoutingPath): RoutingPath {
        return RoutingPath(parts + path.parts)
    }

    override fun toString(): String {
        return parts.map { it.value }.join("/")
    }
}

data class RoutingPathPart(val value: String, val kind: RoutingPathPartKind, val optional : Boolean)

enum class RoutingPathPartKind {
    Constant, Parameter, TailCard
}


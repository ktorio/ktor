package org.jetbrains.ktor.routing

class RoutingPath {
    public val parts: List<RoutingPathPart>

    companion object {
        val root: RoutingPath = RoutingPath(listOf())
        fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val splitted = path.split("/").filter { it.length() > 0 }
            val parts = splitted.map {
                when {
                    it == "*" -> RoutingPathPart("", RoutingPathPartKind.Constant, true)
                    it.startsWith("{") && it.endsWith("}") -> {
                        val signature = it.removeSurrounding("{", "}")
                        when {
                            signature.endsWith("?") -> RoutingPathPart(signature.dropLast(1), RoutingPathPartKind.Parameter, true)
                            signature.endsWith("...") -> RoutingPathPart(signature.dropLast(3), RoutingPathPartKind.TailCard, true)
                            else -> RoutingPathPart(signature, RoutingPathPartKind.Parameter, false)
                        }
                    }
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

data class RoutingPathPart(val value: String, val kind: RoutingPathPartKind, val optional: Boolean)

enum class RoutingPathPartKind {
    Constant, Parameter, TailCard
}


package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*

data class RouteSelectorEvaluation(val succeeded: Boolean, val values: Map<String, List<String>> = mapOf(), val segmentIncrement: Int = 0)

abstract data class RoutingSelector {
    abstract fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation
}

data class ConstantParameterRoutingSelector(val name: String, val value: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters[name]
        if (param != null && param.contains(value))
            return RouteSelectorEvaluation(true)
        return RouteSelectorEvaluation(false)
    }
}

data class ParameterRoutingSelector(val name: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters[name]
        if (param != null)
            return RouteSelectorEvaluation(true, mapOf(name to param))
        return RouteSelectorEvaluation(false)
    }
}

data class OptionalParameterRoutingSelector(val name: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters[name]
        if (param != null)
            return RouteSelectorEvaluation(true, mapOf(name to param))
        return RouteSelectorEvaluation(true)
    }
}

data class UriPartConstantRoutingSelector(val name: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation(index < context.path.parts.size() && context.path.parts[index].value == name, segmentIncrement = 1)
    }
}

data class UriPartParameterRoutingSelector(val name: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.parts.size()) {
            val part = context.path.parts[index].value
            return RouteSelectorEvaluation(true, mapOf(name to listOf(part)), segmentIncrement = 1)
        }
        return RouteSelectorEvaluation(false)
    }
}

data class UriPartOptionalParameterRoutingSelector(val name: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.parts.size()) {
            val part = context.path.parts[index].value
            return RouteSelectorEvaluation(true, mapOf(name to listOf(part)), segmentIncrement = 1)
        }
        return RouteSelectorEvaluation(true)
    }
}

data class UriPartWildcardRoutingSelector() : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.parts.size()) {
            return RouteSelectorEvaluation(true, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation(false)
    }
}

data class UriPartTailcardRoutingSelector(val name: String = "") : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index <= context.path.parts.size()) {
            val values = if (name.isEmpty()) mapOf() else mapOf(name to context.path.parts.drop(index).map { it.value })
            return RouteSelectorEvaluation(true, values, segmentIncrement = context.path.parts.size() - index)
        }
        return RouteSelectorEvaluation(false)
    }
}

data class OrRoutingSelector(val first: RoutingSelector, val second: RoutingSelector) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, index)
        if (result.succeeded)
            return result
        else
            return second.evaluate(context, index)
    }
}

data class AndRoutingSelector(val first: RoutingSelector, val second: RoutingSelector) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, index)
        if (!result1.succeeded)
            return result1
        val result2 = second.evaluate(context, index + result1.segmentIncrement)
        if (!result2.succeeded)
            return result2
        val resultValues = hashMapOf<String, MutableList<String>>()
        for ((key, values) in result1.values) {
            resultValues.getOrPut(key, { arrayListOf() }).addAll(values)
        }
        for ((key, values) in result2.values) {
            resultValues.getOrPut(key, { arrayListOf() }).addAll(values)
        }
        return RouteSelectorEvaluation(true, resultValues, result1.segmentIncrement + result2.segmentIncrement)
    }
}

data class HttpMethodRoutingSelector(val method: HttpMethod) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.verb.method == method)
            return RouteSelectorEvaluation(true)
        return RouteSelectorEvaluation(false)
    }
}

data class HttpHeaderRoutingSelector(val name: String, val value: String) : RoutingSelector() {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.headers[name] == value)
            return RouteSelectorEvaluation(true)
        return RouteSelectorEvaluation(false)
    }
}

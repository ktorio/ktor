package io.ktor.routing

import io.ktor.application.*
import java.util.*

/**
 * Represents a single entry in the [RoutingResolveTrace].
 * @param route specifies instance of [Route] for this entry.
 * @param segmentIndex specifies index in [RoutingResolveTrace.segments] for this entry.
 * @param result specifies resolution result for this entry.
 */
open class RoutingResolveTraceEntry(val route: Route, val segmentIndex: Int, var result: RoutingResolveResult? = null) {
    /**
     * Optional list of children registered for this entry, or null if no children were processed.
     */
    private var children: MutableList<RoutingResolveTraceEntry>? = null

    /**
     * Appends a child to this entry
     */
    fun append(item: RoutingResolveTraceEntry) {
        val items = children ?: mutableListOf<RoutingResolveTraceEntry>().also { children = it }
        items.add(item)
    }

    /**
     * Builds detailed text description for this trace entry, including children.
     */
    open fun buildText(builder: StringBuilder, indent: Int) {
        builder.appendln("  ".repeat(indent) + toString())
        children?.forEach { it.buildText(builder, indent + 1) }
    }

    override fun toString(): String = "$route, segment:$segmentIndex -> $result"
}

/**
 * Represents the trace of routing resolution process for diagnostics.
 * @param call instance of [ApplicationCall] for which this trace was created.
 * @param segments list of [String]s for each path segment supplied for the routing resolution.
 */
class RoutingResolveTrace(val call: ApplicationCall, val segments: List<String>) {
    private val stack = Stack<RoutingResolveTraceEntry>()
    private var routing: RoutingResolveTraceEntry? = null

    private fun register(entry: RoutingResolveTraceEntry) {
        if (stack.empty())
            routing = entry
        else
            stack.peek().append(entry)
    }

    /**
     * Begins processing a [route] at segment with [segmentIndex] in [segments].
     */
    fun begin(route: Route, segmentIndex: Int) {
        stack.push(RoutingResolveTraceEntry(route, segmentIndex))
    }

    /**
     * Finishes processing a [route] at segment with [segmentIndex] in [segments] with the given [result].
     */
    fun finish(route: Route, segmentIndex: Int, result: RoutingResolveResult) {
        val entry = stack.pop()
        require(entry.route == route) { "end should be called for the same route as begin" }
        require(entry.segmentIndex == segmentIndex) { "end should be called for the same segmentIndex as begin" }
        entry.result = result
        register(entry)
    }

    /**
     * Begins and finishes processing a [route] at segment with [segmentIndex] in [segments] with the given [result].
     */
    fun skip(route: Route, segmentIndex: Int, result: RoutingResolveResult) {
        register(RoutingResolveTraceEntry(route, segmentIndex, result))
    }

    override fun toString(): String = "Trace for $segments"

    /**
     * Builds detailed text description for this trace, including all entries.
     */
    fun buildText(): String = buildString {
        appendln(this@RoutingResolveTrace.toString())
        routing?.buildText(this, 0)
    }
}

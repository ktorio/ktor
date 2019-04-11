package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*

/**
 * Represents a result of routing resolution.
 *
 * @property route specifies a routing node for successful resolution, or nearest one for failed.
 */
sealed class RoutingResolveResult(val route: Route) {
    /**
     * Provides all captured values for this result.
     */
    abstract val parameters: Parameters

    /**
     * Represents a successful result
     */
    class Success(route: Route, override val parameters: Parameters) : RoutingResolveResult(route) {
        override fun toString(): String = "SUCCESS${if (parameters.isEmpty()) "" else "; $parameters"} @ $route)"
    }

    /**
     * Represents a failed result
     * @param reason provides information on reason of a failure
     */
    class Failure(route: Route, val reason: String) : RoutingResolveResult(route) {
        override val parameters: Nothing get() = throw UnsupportedOperationException("Parameters are available only when routing resolve succeeds")
        override fun toString(): String = "FAILURE \"$reason\" @ $route)"
    }
}

/**
 * Represents a context in which routing resolution is being performed
 * @param routing root node for resolution to start at
 * @param call instance of [ApplicationCall] to use during resolution
 */
class RoutingResolveContext(val routing: Route, val call: ApplicationCall, private val tracers: List<(RoutingResolveTrace) -> Unit>) {

    /**
     * List of path segments parsed out of a [call]
     */
    val segments: List<String> = parse(call.request.path())

    private val trace = if (tracers.isEmpty()) null else RoutingResolveTrace(call, segments)

    private fun parse(path: String): List<String> {
        if (path.isEmpty() || path == "/") return listOf()
        val length = path.length
        var beginSegment = 0
        var nextSegment = 0
        val segmentCount = path.count { it == '/' }
        val segments = ArrayList<String>(segmentCount)
        while (nextSegment < length) {
            nextSegment = path.indexOf('/', beginSegment)
            if (nextSegment == -1) {
                nextSegment = length
            }
            if (nextSegment == beginSegment) {
                // empty path segment, skip it
                beginSegment = nextSegment + 1
                continue
            }
            val segment = path.decodeURLPart(beginSegment, nextSegment)
            segments.add(segment)
            beginSegment = nextSegment + 1
        }
        return segments
    }

    /**
     * Executes resolution procedure in this context and returns [RoutingResolveResult]
     */
    fun resolve(): RoutingResolveResult {
        val root = routing
        val rootResult = root.selector.evaluate(this, 0)
        if (!rootResult.succeeded) {
            return rootResolveFailed(root)
        }

        val result = resolve(root, rootResult.segmentIncrement)
        trace?.apply { tracers.forEach { it(this) } }
        return result
    }

    private fun rootResolveFailed(root: Route): RoutingResolveResult.Failure {
        return RoutingResolveResult.Failure(root, "rootPath didn't match").also { result ->
            trace?.skip(root, 0, result)
        }
    }

    private fun resolve(entry: Route, segmentIndex: Int): RoutingResolveResult {
        trace?.begin(entry, segmentIndex)

        // last failed entry for diagnostics
        var failEntry: Route? = null
        // best matched entry (with highest quality)
        var bestResult: RoutingResolveResult? = null
        var bestQuality = 0.0
        var bestChild: Route? = null

        // iterate using indices to avoid creating iterator
        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            val selectorResult = child.selector.evaluate(this, segmentIndex)
            if (!selectorResult.succeeded) {
                trace?.skip(child, segmentIndex, RoutingResolveResult.Failure(child, "Selector didn't match"))
                continue // selector didn't match, skip entire subtree
            }

            val immediateSelectQuality = selectorResult.quality

            if (immediateSelectQuality < bestQuality) {
                trace?.skip(child, segmentIndex, RoutingResolveResult.Failure(child, "Better match was already found"))
                continue
            }

            if (immediateSelectQuality == bestQuality) {
                // ambiguity, compare immediate child quality
                if (bestChild!!.selector.quality >= child.selector.quality) {
                    trace?.skip(child, segmentIndex, RoutingResolveResult.Failure(child, "Lost in ambiguity tie"))
                    continue
                }
            }

            val subtreeResult = resolve(child, segmentIndex + selectorResult.segmentIncrement)
            when (subtreeResult) {
                is RoutingResolveResult.Failure -> {
                    // subtree didn't match, skip to next child, remember first failed entry
                    if (failEntry == null) {
                        failEntry = subtreeResult.route
                    }
                }
                is RoutingResolveResult.Success -> {
                    bestChild = child
                    bestQuality = immediateSelectQuality
                    bestResult = if (selectorResult.parameters.isEmpty()) {
                        // do not allocate new RoutingResolveResult if it will be the same as subtreeResult
                        // TODO: Evaluate if we can make RoutingResolveResult mutable altogether and avoid allocations
                        subtreeResult
                    } else {
                        val combinedValues = selectorResult.parameters + subtreeResult.parameters
                        RoutingResolveResult.Success(subtreeResult.route, combinedValues)
                    }
                }
            }
        }

        val result = if (segmentIndex == segments.size && entry.handlers.isNotEmpty()) {
            if (bestResult != null && bestQuality > RouteSelectorEvaluation.qualityMissing) {
                // child match is better than missing optional parameter, so choose it
                bestResult
            } else {
                // no child matched, or child matched optionally and this node has a handler
                RoutingResolveResult.Success(entry, Parameters.Empty)
            }
        } else {
            if (bestResult != null) {
                // child matched
                bestResult
            } else {
                // nothing more to match and no handler, or there are more segments and no matched child
                val reason = if (segmentIndex == segments.size) "Segments exhausted but no handlers found" else "Not all segments matched"
                RoutingResolveResult.Failure(failEntry ?: entry, reason)
            }
        }

        trace?.finish(entry, segmentIndex, result)
        return result
    }

}


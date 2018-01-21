package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*

/**
 * Represents a result of routing resolution
 *
 * @param succeeded indicates if resolution succeeded
 * @param route specifies a routing node for successful resolution, or nearest one for failed
 * @param parameters holds all captured values from selectors
 * @param quality represents quality value for resolution result
 */
data class RoutingResolveResult(val succeeded: Boolean,
                                val route: Route,
                                val parameters: Parameters,
                                val quality: Double)

/**
 * Represents a context in which routing resolution is being performed
 * @param routing root node for resolution to start at
 * @param call instance of [ApplicationCall] to use during resolution
 */
class RoutingResolveContext(val routing: Route, val call: ApplicationCall) {
    /**
     * List of path segments parsed out of a [call]
     */
    val segments: List<String> = parse(call.request.path())

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
            val segment = decodeURLPart(path, beginSegment, nextSegment)
            segments.add(segment)
            beginSegment = nextSegment + 1
        }
        return segments
    }

    /**
     * Executes resolution procedure in this context and returns [RoutingResolveResult]
     */
    fun resolve(): RoutingResolveResult {
        return resolve(routing, this, 0)
    }

    internal fun resolve(entry: Route, request: RoutingResolveContext, segmentIndex: Int): RoutingResolveResult {
        // last failed entry for diagnostics
        var failEntry: Route? = null
        // best matched entry (with highest quality)
        var bestResult: RoutingResolveResult? = null
        var bestChild: Route? = null

        // iterate using indices to avoid creating iterator
        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            val selectorResult = child.selector.evaluate(request, segmentIndex)
            if (!selectorResult.succeeded)
                continue // selector didn't match, skip entire subtree

            val subtreeResult = resolve(child, request, segmentIndex + selectorResult.segmentIncrement)
            if (!subtreeResult.succeeded) {
                // subtree didn't match, skip to next child, remember first failed entry
                if (failEntry == null) {
                    failEntry = subtreeResult.route
                }
                continue
            }

            val bestMatchQuality = bestResult?.quality ?: 0.0
            val immediateSelectQuality = selectorResult.quality

            if (immediateSelectQuality < bestMatchQuality)
                continue

            if (immediateSelectQuality == bestMatchQuality) {
                // ambiguity, compare immediate child quality
                if (bestChild!!.selector.quality >= child.selector.quality)
                    continue
            }

            bestChild = child

            // only calculate values if match is better then previous one
            if (selectorResult.parameters.isEmpty() && immediateSelectQuality == subtreeResult.quality) {
                // do not allocate new RoutingResolveResult if it will be the same as subtreeResult
                // TODO: Evaluate if we can make RoutingResolveResult mutable altogether and avoid allocations
                bestResult = subtreeResult
            } else {
                val combinedValues = selectorResult.parameters + subtreeResult.parameters
                bestResult = RoutingResolveResult(true, subtreeResult.route, combinedValues, immediateSelectQuality)
            }
        }

        // no child matched, match is either current entry if path is done & there is a handler, or failure
        if (segmentIndex == request.segments.size && entry.handlers.isNotEmpty()) {
            if (bestResult != null && bestResult.quality > RouteSelectorEvaluation.qualityMissing)
                return bestResult

            return RoutingResolveResult(true, entry, Parameters.Empty, 1.0)
        }

        return bestResult ?: RoutingResolveResult(false, failEntry ?: entry, Parameters.Empty, 0.0)
    }

}

/**
 * Exception indicating a failure in a routing resolution process
 */
class RoutingResolutionException(message: String) : Exception(message)


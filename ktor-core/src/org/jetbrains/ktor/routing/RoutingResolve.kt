package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: Route,
                                val values: ValuesMap,
                                val quality: Double)

class RoutingResolveContext(val routing: Route,
                            val call: ApplicationCall,
                            val parameters: ValuesMap = ValuesMap.Empty, // TODO don't pass parameters and headers, use call instead
                            val headers: ValuesMap = ValuesMap.Empty) {
    val path = parse(call.request.path())

    private fun parse(path: String): List<String> {
        if (path == "/") return listOf()
        return path.split("/").filter { it.length > 0 }.map { decodeURLPart(it) }
    }

    private fun combineQuality(quality1: Double, quality2: Double): Double {
        return quality1 * quality2
    }

    fun resolve(): RoutingResolveResult {
        return resolve(routing, this, 0)
    }

    internal fun resolve(entry: Route, request: RoutingResolveContext, segmentIndex: Int): RoutingResolveResult {
        // last failed entry for diagnostics
        var failEntry: Route? = null
        // best matched entry (with highest quality)
        var bestResult: RoutingResolveResult? = null

        // iterate using indices to avoid creating iterator
        for (childIndex in 0..entry.children.lastIndex) {
            val child = entry.children[childIndex]
            val result = child.selector.evaluate(request, segmentIndex)
            if (!result.succeeded)
                continue // selector didn't match, skip entire subtree

            val subtreeResult = resolve(child, request, segmentIndex + result.segmentIncrement)
            if (!subtreeResult.succeeded) {
                // subtree didn't match, skip to next child, remember first failed entry
                if (failEntry == null) {
                    failEntry = subtreeResult.entry
                }
                continue
            }

            // calculate match quality of this selector match and subtree
            val combinedQuality = combineQuality(subtreeResult.quality, result.quality)
            if (combinedQuality <= bestResult?.quality ?: 0.0)
                continue

            // only calculate values if match is better then previous one
            val combinedValues = result.values + subtreeResult.values
            bestResult = RoutingResolveResult(true, subtreeResult.entry, combinedValues, combinedQuality)
        }

        if (bestResult != null)
            return bestResult

        // no child matched, match is either current entry if path is done & there is a handler, or failure
        if (segmentIndex == request.path.size && entry.handlers.isNotEmpty())
            return RoutingResolveResult(true, entry, ValuesMap.Empty, 1.0)
        else
            return RoutingResolveResult(false, failEntry ?: entry, ValuesMap.Empty, 0.0)
    }
}



package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: ValuesMap,
                                val quality: Double)

class RoutingResolveContext(val routing: RoutingEntry,
                            val verb: HttpRequestLine,
                            val parameters: ValuesMap = ValuesMap.Empty,
                            val headers: ValuesMap = ValuesMap.Empty) {
    val path = parse(verb.path())

    private fun parse(path: String): List<String> {
        if (path == "/") return listOf()
        return path.split("/").filter { it.length > 0 }.map { decodeURLPart(it) }
    }

    private fun combineQuality(quality1: Double, quality2: Double): Double {
        return quality1 * quality2
    }

    internal fun resolve(): RoutingResolveResult {
        return resolve(routing, this, 0)
    }

    internal fun resolve(entry: RoutingEntry, request: RoutingResolveContext, segmentIndex: Int): RoutingResolveResult {
        // last failed entry for diagnostics
        var failEntry: RoutingEntry? = null
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
            val combinedValues = when {
                result.values.isEmpty() -> subtreeResult.values
                subtreeResult.values.isEmpty() -> result.values
                else -> ValuesMap.build {
                    appendAll(result.values)
                    appendAll(subtreeResult.values)
                }
            }
            bestResult = RoutingResolveResult(true, subtreeResult.entry, combinedValues, combinedQuality)
        }

        if (bestResult != null)
            return bestResult

        // no child matched, match is either current entry if path is done, or failure
        if (segmentIndex == request.path.size)
            return RoutingResolveResult(true, entry, ValuesMap.Empty, 1.0)
        else
            return RoutingResolveResult(false, failEntry ?: entry, ValuesMap.Empty, 0.0)
    }
}



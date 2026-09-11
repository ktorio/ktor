/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import java.nio.file.Path

internal class PathExclusions(patterns: List<String>) {
    private val excludedPatternSegments = patterns.map { it.split('/') }

    operator fun contains(path: Path): Boolean {
        val pathSegments = path.iterator().asSequence().map(Path::toString).toList()
        return excludedPatternSegments.any { matchesPattern(pathSegments, it) }
    }
}

private fun matchesPattern(
    pathSegments: List<String>,
    patternSegments: List<String>,
    pathIndex: Int = 0,
    patternIndex: Int = 0,
): Boolean {
    if (patternIndex == patternSegments.size) return pathIndex == pathSegments.size
    if (patternSegments[patternIndex] == "**") {
        var nextPatternIndex = patternIndex + 1
        while (nextPatternIndex < patternSegments.size && patternSegments[nextPatternIndex] == "**") {
            nextPatternIndex++
        }
        if (nextPatternIndex == patternSegments.size) return true

        for (candidatePathIndex in pathIndex until pathSegments.size) {
            if (pathSegments[candidatePathIndex] == patternSegments[nextPatternIndex] &&
                matchesPattern(pathSegments, patternSegments, candidatePathIndex + 1, nextPatternIndex + 1)
            ) {
                return true
            }
        }
        return false
    }
    return pathIndex < pathSegments.size &&
        pathSegments[pathIndex] == patternSegments[patternIndex] &&
        matchesPattern(pathSegments, patternSegments, pathIndex + 1, patternIndex + 1)
}

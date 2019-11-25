/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*

internal fun rollFiles(
    fileSystem: FileSystem,
    fileName: String,
    view: CachedFilesView,
    pattern: FilePathPattern,
    date: GMTDate
) {
    val moreSpecificPattern = pattern.withConstantDate(date)
    val numberOffset = findNumberOffset(moreSpecificPattern)

    view.listPaths()
        .filter { moreSpecificPattern.matches(it) && it != fileName }
        .map {
            val fileNumber = parseNumber(it, numberOffset)
            Pair(it, fileNumber)
        }
        .sortedByDescending { it.second }
        .forEach { (path, number) ->
            val newName = moreSpecificPattern.format(date, number + 1)
            fileSystem.rename(path, newName)
        }

    val firstName = moreSpecificPattern.format(date, 1)
    fileSystem.rename(fileName, firstName)
}

private fun parseNumber(path: String, numberOffset: Int): Int {
    var end = numberOffset
    while (end < path.length) {
        if (path[end] !in '0'..'9') {
            break
        }
        end++
    }

    check(numberOffset != end)

    return path.substring(numberOffset, end).toInt()
}

private fun findNumberOffset(moreSpecificPattern: FilePathPattern): Int {
    var startCharacterIndex = 0
    for (index in moreSpecificPattern.pathComponentsPatterns.indices) {
        val component = moreSpecificPattern.pathComponentsPatterns[index]
        if (component is FilePathPattern.PatternOrConstant.Constant) {
            startCharacterIndex += component.text.length
            startCharacterIndex++ // separator character
            continue
        }

        component.relatedComponents.forEach { subPart ->
            when (subPart) {
                is FilePathPattern.Component.ConstantPart -> startCharacterIndex += subPart.text.length
                is FilePathPattern.Component.Date -> error("Should be filtered out with withConstantDate()")
                is FilePathPattern.Component.Separator -> error("Shouldn't be inside of component")
                is FilePathPattern.Component.Number -> {
                    return startCharacterIndex
                }
            }
        }
    }

    error("Number pattern is not found")
}

private fun FilePathPattern.withConstantDate(date: GMTDate): FilePathPattern {
    if (parts.none { it is FilePathPattern.Component.Date }) return this

    return FilePathPattern(parts.map { part ->
        when (part) {
            is FilePathPattern.Component.Date -> FilePathPattern.Component.ConstantPart(date.format(part.format))
            else -> part
        }
    })
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import io.ktor.util.logging.*


/**
 * Custom predicate to filter log records by logger name and level.
 */
fun LoggingConfigBuilder.nameFilter(block: (name: String, level: Level) -> Boolean) {
    filter {
        val name = name ?: return@filter
        if (!block(name, level)) {
            discard()
        }
    }
}

/**
 * Filter messages with the specified [namePattern] to [level].
 */
fun LoggingConfigBuilder.level(namePattern: String, level: Level) {
    val starCount = namePattern.count { it == '*' }

    if (starCount == 0) {
        filter {
            if (this.level < level && name == namePattern) {
                discard()
            }
        }
        return
    }
    if (starCount > 1) return anyStarCountFilter(namePattern, level)

    if (namePattern == "*") {
        filter {
            if (this.level < level) {
                discard()
            }
        }
        return
    }

    val starPosition = namePattern.indexOf('*')
    if (starPosition == 0) {
        val suffix = namePattern.drop(1)
        filter {
            if (this.level < level && name?.endsWith(suffix) == true) {
                discard()
            }
        }
        return
    }

    if (starPosition == namePattern.lastIndex) {
        val prefix = namePattern.dropLast(1)
        filter {
            if (this.level < level && name?.startsWith(prefix) == true) {
                discard()
            }
        }
        return
    }

    val prefix = namePattern.substring(0, starPosition)
    val suffix = namePattern.substring(starPosition + 1)

    filter {
        if (this.level < level && name?.let { it.startsWith(prefix) && it.endsWith(suffix) } == true) {
            discard()
        }
    }
}

private fun LoggingConfigBuilder.anyStarCountFilter(namePattern: String, level: Level) {
    require(namePattern.isNotEmpty())

    val parts = namePattern.split("*").filter { it.isNotEmpty() }
    val fixedStart = namePattern.first() != '*'
    val fixedEnd = namePattern.last() != '*'

    filter {
        if (this.level >= level) return@filter

        if (parts.isNotEmpty()) {
            val name = name ?: return@filter
            var currentOffset = 0

            parts.forEachIndexed { partIndex, part ->
                if (currentOffset >= name.length) return@filter
                val index = name.indexOf(part, currentOffset)
                if (index == -1) return@filter
                if (partIndex == 0 && fixedStart && index != 0) return@filter
                if (fixedEnd && partIndex == parts.lastIndex && index + part.length != name.length) return@filter

                currentOffset = index + part.length
            }
        }

        discard()
    }
}

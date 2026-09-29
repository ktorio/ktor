/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies.publicsuffix

import io.ktor.client.plugins.cookies.PublicSuffixRules

internal const val PUBLIC_SUFFIX_RESOURCE: String =
    "io/ktor/client/plugins/cookies/publicsuffix/PublicSuffixDatabase.list"

internal class BinaryPublicSuffixMatcher(
    private val rules: ByteArray,
    private val exceptions: ByteArray
) : PublicSuffixRules {
    override fun isPublicSuffix(canonicalDomain: String): Boolean {
        val domain = canonicalDomain.encodeToByteArray()
        if (exceptions.binarySearch(domain)) return false
        if (rules.binarySearch(domain)) return true

        val firstDot = canonicalDomain.indexOf('.')
        if (firstDot >= 0) {
            val wildcard = "*.${canonicalDomain.substring(firstDot + 1)}".encodeToByteArray()
            if (rules.binarySearch(wildcard)) return true
        }

        return firstDot < 0
    }

    companion object {
        fun from(resource: ByteArray): BinaryPublicSuffixMatcher {
            check(resource.size >= 8) { "The bundled Public Suffix List is truncated" }

            val rulesSize = resource.readInt(0)
            check(rulesSize > 0 && rulesSize <= resource.size - 8) {
                "The bundled Public Suffix List has an invalid rules block"
            }
            val rulesEnd = 4 + rulesSize

            val exceptionsSize = resource.readInt(rulesEnd)
            check(exceptionsSize >= 0 && exceptionsSize == resource.size - rulesEnd - 4) {
                "The bundled Public Suffix List has an invalid exceptions block"
            }

            val rules = resource.copyOfRange(4, rulesEnd)
            val exceptions = resource.copyOfRange(rulesEnd + 4, resource.size)
            rules.validateRuleBlock("rules", allowWildcard = true)
            exceptions.validateRuleBlock("exceptions", allowWildcard = false)
            return BinaryPublicSuffixMatcher(rules, exceptions)
        }
    }
}

private fun ByteArray.readInt(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 24) or
        ((this[offset + 1].toInt() and 0xff) shl 16) or
        ((this[offset + 2].toInt() and 0xff) shl 8) or
        (this[offset + 3].toInt() and 0xff)

private fun ByteArray.validateRuleBlock(name: String, allowWildcard: Boolean) {
    if (isEmpty()) return
    check(last() == '\n'.code.toByte()) { "The bundled Public Suffix List $name block is truncated" }

    var start = 0
    var previousStart = -1
    var previousEnd = -1
    for (index in indices) {
        if (this[index] != '\n'.code.toByte()) continue
        check(index > start) { "The bundled Public Suffix List $name block contains an empty rule" }
        validateRule(start, index, name, allowWildcard)
        check(previousStart < 0 || compareRanges(previousStart, previousEnd, start, index) < 0) {
            "The bundled Public Suffix List $name block is not strictly sorted"
        }
        previousStart = start
        previousEnd = index
        start = index + 1
    }
}

private fun ByteArray.validateRule(start: Int, end: Int, name: String, allowWildcard: Boolean) {
    var wildcard = false
    for (index in start until end) {
        val character = this[index].toInt().toChar()
        when (character) {
            in 'a'..'z', in '0'..'9', '-', '.' -> Unit

            '*' if allowWildcard && index == start && index + 1 < end &&
                this[index + 1] == '.'.code.toByte() -> wildcard = true

            else -> error("The bundled Public Suffix List $name block contains an invalid rule")
        }
    }
    check('*'.code.toByte() !in copyOfRange(start, end) || wildcard) {
        "The bundled Public Suffix List $name block contains an invalid wildcard"
    }
}

private fun ByteArray.compareRanges(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Int {
    val limit = minOf(firstEnd - firstStart, secondEnd - secondStart)
    for (index in 0 until limit) {
        val comparison =
            (this[firstStart + index].toInt() and 0xff) - (this[secondStart + index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return (firstEnd - firstStart).compareTo(secondEnd - secondStart)
}

/**
 * Binary search adapted from OkHttp 5.5.0's `PublicSuffixDatabase` without its Okio dependency.
 *
 * Source: https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/publicsuffix/PublicSuffixDatabase.kt
 */
private fun ByteArray.binarySearch(target: ByteArray): Boolean {
    var low = 0
    var high = lastIndex

    while (low <= high) {
        var start = (low + high) ushr 1
        while (start > 0 && this[start - 1] != '\n'.code.toByte()) start--

        var end = start
        while (end < size && this[end] != '\n'.code.toByte()) end++

        val comparison = compareRule(start, end, target)
        when {
            comparison < 0 -> high = start - 1
            comparison > 0 -> low = end + 1
            else -> return true
        }
    }

    return false
}

private fun ByteArray.compareRule(start: Int, end: Int, target: ByteArray): Int {
    val ruleSize = end - start
    val limit = minOf(ruleSize, target.size)
    for (index in 0 until limit) {
        val comparison = (target[index].toInt() and 0xff) - (this[start + index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return target.size.compareTo(ruleSize)
}

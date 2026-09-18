/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.client.*
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

private const val PUBLIC_SUFFIX_LIST_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

/**
 * Loads the Public Suffix List from [url] and creates a cookie policy that rejects explicit public-suffix domains.
 *
 * The list is loaded once, and both its ICANN and private sections are used. The returned policy does not refresh the
 * list automatically. This policy accepts cookies without a domain attribute and cookies with IP addresses.
 *
 * @param url the URL of a UTF-8 Public Suffix List.
 * @return a cookie acceptance policy backed by the loaded list.
 * @throws IllegalStateException if loading [url] returns an unsuccessful HTTP status.
 * @throws IllegalArgumentException if the returned list is empty or malformed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.loadPublicSuffixCookiePolicy)
 */
public suspend fun HttpClient.loadPublicSuffixCookiePolicy(
    url: String = PUBLIC_SUFFIX_LIST_URL
): CookieAcceptancePolicy {
    val response = get(url) { expectSuccess = true }
    val rules = PublicSuffixRules.parse(response.bodyAsText())
    return PublicSuffixCookiePolicy(rules)
}

private class PublicSuffixCookiePolicy(
    private val rules: PublicSuffixRules
) : CookieAcceptancePolicy {
    override suspend fun shouldAccept(requestUrl: Url, cookie: Cookie): Boolean {
        val domain = cookie.domain?.takeUnless { it.isBlank() }?.trimStart('.') ?: return true
        if (hostIsIp(domain)) return true

        val canonicalDomain = try {
            canonicalizeDomain(domain)
        } catch (_: IllegalArgumentException) {
            return false
        }

        return !rules.isPublicSuffix(canonicalDomain)
    }
}

private class PublicSuffixRules(
    private val exact: Set<String>,
    private val wildcards: Set<String>,
    private val exceptions: Set<String>
) {
    fun isPublicSuffix(domain: String): Boolean {
        val labels = domain.split('.')
        var matchingRuleLabels = 1

        for (index in labels.indices) {
            val suffix = labels.subList(index, labels.size).joinToString(".")
            if (suffix in exceptions) return false
            if (suffix in exact) matchingRuleLabels = maxOf(matchingRuleLabels, labels.size - index)
            if (index > 0 && suffix in wildcards) {
                matchingRuleLabels = maxOf(matchingRuleLabels, labels.size - index + 1)
            }
        }

        return labels.size == matchingRuleLabels
    }

    companion object {
        fun parse(content: String): PublicSuffixRules {
            require(content.isNotBlank()) { "The Public Suffix List is empty" }

            val exact = mutableSetOf<String>()
            val wildcards = mutableSetOf<String>()
            val exceptions = mutableSetOf<String>()

            content.lineSequence().forEachIndexed { index, sourceLine ->
                val line = sourceLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed

                val (target, rule) = when {
                    line.startsWith("!", ignoreCase = false) -> exceptions to line.drop(1)
                    line.startsWith("*.", ignoreCase = false) -> wildcards to line.drop(2)
                    else -> exact to line
                }

                require(rule.isNotEmpty() && '*' !in rule && '!' !in rule && rule.none { it.isWhitespace() }) {
                    "Malformed Public Suffix List rule at line ${index + 1}: $line"
                }

                try {
                    target += canonicalizeDomain(rule)
                } catch (cause: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Malformed Public Suffix List rule at line ${index + 1}: $line",
                        cause
                    )
                }
            }

            require(exact.isNotEmpty() || wildcards.isNotEmpty() || exceptions.isNotEmpty()) {
                "The Public Suffix List contains no rules"
            }
            return PublicSuffixRules(exact, wildcards, exceptions)
        }
    }
}

private fun canonicalizeLabel(label: String): String {
    require(label.isNotEmpty()) { "Empty domain label" }

    val canonicalLabel = when {
        label.startsWith("xn--") -> {
            val decoded = Punycode.decode(label.drop(4))
            require(decoded.any { it.code >= 0x80 }) { "Invalid Punycode label: $label" }
            val encoded = Punycode.encode(decoded)
            require(encoded == label.drop(4)) { "Non-canonical Punycode label: $label" }
            "xn--$encoded"
        }

        label.any { it.code >= 0x80 } -> "xn--${Punycode.encode(label)}"

        else -> label
    }

    require(canonicalLabel.length <= 63) {
        "Invalid domain label: $label"
    }
    require(canonicalLabel.first() != '-' && canonicalLabel.last() != '-') {
        "Invalid domain label: $label"
    }
    require(canonicalLabel.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
        "Invalid domain label: $label"
    }

    return canonicalLabel
}

private fun canonicalizeDomain(domain: String): String {
    require(domain.isNotEmpty() && domain.length <= 253) { "Invalid domain: $domain" }

    val result = domain
        .lowercase()
        .split('.')
        .joinToString(separator = ".", transform = ::canonicalizeLabel)

    require(result.length <= 253) { "Invalid domain: $domain" }
    return result
}

private object Punycode {
    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128

    fun encode(value: String): String {
        val input = value.toCodePoints()
        require(input.isNotEmpty()) { "A Punycode label cannot be empty" }

        val output = StringBuilder()
        input.filter { it < INITIAL_N }.forEach { output.append(it.toChar()) }

        val basicCount = output.length
        var handled = basicCount
        if (basicCount > 0 && handled < input.size) output.append('-')

        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS

        while (handled < input.size) {
            val next = input.filter { it >= n }.minOrNull() ?: error("Invalid Punycode input")
            delta = addExact(delta, multiplyExact((next - n).toLong(), (handled + 1).toLong()))
            n = next

            for (codePoint in input) {
                if (codePoint < n) delta = addExact(delta, 1)
                if (codePoint != n) continue

                var q = delta
                var k = BASE
                while (true) {
                    val threshold = threshold(k, bias)
                    if (q < threshold) break
                    output.append(encodeDigit(threshold + (q - threshold) % (BASE - threshold)))
                    q = (q - threshold) / (BASE - threshold)
                    k += BASE
                }
                output.append(encodeDigit(q))
                bias = adapt(delta, handled + 1, handled == basicCount)
                delta = 0
                handled++
            }

            delta = addExact(delta, 1)
            n++
        }

        return output.toString()
    }

    fun decode(value: String): String {
        require(value.isNotEmpty()) { "A Punycode label cannot be empty" }

        val output = mutableListOf<Int>()
        val delimiter = value.lastIndexOf('-')
        var position = 0
        if (delimiter >= 0) {
            value.take(delimiter).forEach {
                require(it.code < INITIAL_N) { "Invalid basic Punycode code point" }
                output += it.code
            }
            position = delimiter + 1
        }

        var n = INITIAL_N
        var index = 0L
        var bias = INITIAL_BIAS

        while (position < value.length) {
            val oldIndex = index
            var weight = 1L
            var k = BASE

            while (true) {
                require(position < value.length) { "Incomplete Punycode sequence" }
                val digit = decodeDigit(value[position++])
                index = addExact(index, multiplyExact(digit, weight))
                val threshold = threshold(k, bias)
                if (digit < threshold) break
                weight = multiplyExact(weight, BASE - threshold)
                k += BASE
            }

            val outputSize = output.size + 1
            bias = adapt(index - oldIndex, outputSize, oldIndex == 0L)
            n += (index / outputSize).toInt()
            require(n in 0..0x10ffff && n !in 0xd800..0xdfff) { "Invalid Punycode code point" }
            index %= outputSize
            output.add(index.toInt(), n)
            index++
        }

        return output.toUnicodeString()
    }

    private fun adapt(sourceDelta: Long, points: Int, firstTime: Boolean): Int {
        var delta = if (firstTime) sourceDelta / DAMP else sourceDelta / 2
        delta += delta / points
        var k = 0
        while (delta > ((BASE - T_MIN) * T_MAX) / 2) {
            delta /= BASE - T_MIN
            k += BASE
        }
        return k + (((BASE - T_MIN + 1) * delta) / (delta + SKEW)).toInt()
    }

    private fun threshold(k: Int, bias: Int): Long = when {
        k <= bias -> T_MIN.toLong()
        k >= bias + T_MAX -> T_MAX.toLong()
        else -> (k - bias).toLong()
    }

    private fun encodeDigit(digit: Long): Char {
        require(digit in 0 until BASE) { "Invalid Punycode digit" }
        return if (digit < 26) ('a'.code + digit.toInt()).toChar() else ('0'.code + digit.toInt() - 26).toChar()
    }

    private fun decodeDigit(value: Char): Long = when (value) {
        in 'a'..'z' -> (value.code - 'a'.code).toLong()
        in 'A'..'Z' -> (value.code - 'A'.code).toLong()
        in '0'..'9' -> (value.code - '0'.code + 26).toLong()
        else -> throw IllegalArgumentException("Invalid Punycode digit: $value")
    }

    private fun addExact(left: Long, right: Long): Long {
        require(right <= Long.MAX_VALUE - left) { "Punycode overflow" }
        return left + right
    }

    private fun multiplyExact(left: Long, right: Long): Long {
        require(left == 0L || right <= Long.MAX_VALUE / left) { "Punycode overflow" }
        return left * right
    }
}

private fun String.toCodePoints(): List<Int> = buildList {
    var index = 0
    while (index < length) {
        when (val first = this@toCodePoints[index].code) {
            in 0xd800..0xdbff -> {
                require(index + 1 < length) { "Unpaired high surrogate" }
                val second = this@toCodePoints[index + 1].code
                require(second in 0xdc00..0xdfff) { "Unpaired high surrogate" }
                add(0x10000 + ((first - 0xd800) shl 10) + (second - 0xdc00))
                index += 2
            }

            in 0xdc00..0xdfff -> throw IllegalArgumentException("Unpaired low surrogate")

            else -> {
                add(first)
                index++
            }
        }
    }
}

private fun List<Int>.toUnicodeString(): String = buildString {
    for (codePoint in this@toUnicodeString) {
        if (codePoint <= 0xffff) {
            append(codePoint.toChar())
        } else {
            val supplementary = codePoint - 0x10000
            append((0xd800 + (supplementary shr 10)).toChar())
            append((0xdc00 + (supplementary and 0x3ff)).toChar())
        }
    }
}

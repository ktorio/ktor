/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Escapes the characters in a String using HTML entities
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.escapeHTML)
 */
public fun String.escapeHTML(): String {
    val text = this@escapeHTML
    if (text.isEmpty()) return text

    return buildString(length) {
        for (element in text) {
            when (element) {
                '\'' -> append("&#x27;")
                '\"' -> append("&quot;")
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(element)
            }
        }
    }
}

/**
 * Splits the given string into two parts before and after separator.
 *
 * Useful together with destructuring declarations
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.chomp)
 */
public inline fun String.chomp(
    separator: String,
    onMissingDelimiter: () -> Pair<String, String>
): Pair<String, String> {
    return when (val idx = indexOf(separator)) {
        -1 -> onMissingDelimiter()
        else -> substring(0, idx) to substring(idx + separator.length)
    }
}

/**
 * Does the same as the regular [toLowerCase] except that locale-specific rules are not applied to ASCII characters
 * so latin characters are converted by the original english rules.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.toLowerCasePreservingASCIIRules)
 */
public fun String.toLowerCasePreservingASCIIRules(): String {
    val firstIndex = indexOfFirst {
        toLowerCasePreservingASCII(it) != it
    }

    if (firstIndex == -1) {
        return this
    }

    val original = this
    return buildString(length) {
        append(original, 0, firstIndex)

        for (index in firstIndex..original.lastIndex) {
            append(toLowerCasePreservingASCII(original[index]))
        }
    }
}

/**
 * Does the same as the regular [toUpperCase] except that locale-specific rules are not applied to ASCII characters
 * so latin characters are converted by the original english rules.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.toUpperCasePreservingASCIIRules)
 */
public fun String.toUpperCasePreservingASCIIRules(): String {
    val firstIndex = indexOfFirst {
        toUpperCasePreservingASCII(it) != it
    }

    if (firstIndex == -1) {
        return this
    }

    val original = this
    return buildString(length) {
        append(original, 0, firstIndex)

        for (index in firstIndex..original.lastIndex) {
            append(toUpperCasePreservingASCII(original[index]))
        }
    }
}

private fun toLowerCasePreservingASCII(ch: Char): Char = when (ch) {
    in 'A'..'Z' -> ch + 32
    in '\u0000'..'\u007f' -> ch
    else -> ch.lowercaseChar()
}

private fun toUpperCasePreservingASCII(ch: Char): Char = when (ch) {
    in 'a'..'z' -> ch - 32
    in '\u0000'..'\u007f' -> ch
    else -> ch.lowercaseChar()
}

/**
 * Cache of pre-computed CaseInsensitiveString instances for common HTTP header names.
 * Reduces allocations for frequently used headers.
 */
private val commonHeaderNames: Map<String, CaseInsensitiveString> = buildCommonHeaderCache()

private fun buildCommonHeaderCache(): Map<String, CaseInsensitiveString> {
    val headers = arrayOf(
        "Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language", "Accept-Ranges",
        "Age", "Allow", "Authorization", "Cache-Control", "Connection",
        "Content-Disposition", "Content-Encoding", "Content-Language", "Content-Length",
        "Content-Location", "Content-Range", "Content-Type", "Cookie", "Date",
        "ETag", "Expect", "Expires", "From", "Host",
        "If-Match", "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since",
        "Last-Modified", "Location", "Max-Forwards", "Origin", "Pragma",
        "Proxy-Authenticate", "Proxy-Authorization", "Range", "Referer", "Retry-After",
        "Server", "Set-Cookie", "Transfer-Encoding", "Upgrade", "User-Agent",
        "Vary", "Via", "Warning", "WWW-Authenticate",
        "Access-Control-Allow-Origin", "Access-Control-Allow-Methods",
        "Access-Control-Allow-Headers", "Access-Control-Allow-Credentials",
        "Access-Control-Expose-Headers", "Access-Control-Max-Age",
        "Access-Control-Request-Method", "Access-Control-Request-Headers",
        "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Request-ID"
    )
    val result = HashMap<String, CaseInsensitiveString>(headers.size * 2)
    for (header in headers) {
        result[header] = CaseInsensitiveString(header)
        val lower = header.lowercase()
        if (lower != header) {
            result[lower] = CaseInsensitiveString(lower)
        }
    }
    return result
}

internal fun String.caseInsensitive(): CaseInsensitiveString =
    commonHeaderNames[this] ?: CaseInsensitiveString(this)

internal class CaseInsensitiveString(val content: String) {
    private val hash: Int

    init {
        var temp = 0
        for (element in content) {
            temp = temp * 31 + element.lowercaseChar().code
        }

        hash = temp
    }

    override fun equals(other: Any?): Boolean =
        (other as? CaseInsensitiveString)?.content?.equals(content, ignoreCase = true) == true

    override fun hashCode(): Int = hash

    override fun toString(): String = content
}

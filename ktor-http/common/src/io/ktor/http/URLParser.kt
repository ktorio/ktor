package io.ktor.http

import io.ktor.util.*


/**
 * Take url parts from [urlString]
 * throws [URLParserException]
 */
fun URLBuilder.takeFrom(urlString: String): URLBuilder {
    return try {
        takeFromUnsafe(urlString)
    } catch (cause: Throwable) {
        throw URLParserException(urlString, cause)
    }
}

/**
 * Thrown when failed to parse URL
 */
class URLParserException(urlString: String, cause: Throwable) : IllegalStateException(
    "Fail to parse url: $urlString", cause
)

internal fun URLBuilder.takeFromUnsafe(urlString: String): URLBuilder {
    var startIndex = urlString.indexOfFirst { !it.isWhitespace() }
    val endIndex = urlString.indexOfLast { !it.isWhitespace() } + 1

    val schemeLength = findScheme(urlString, startIndex, endIndex)
    if (schemeLength > 0) {
        val scheme = urlString.substring(startIndex, startIndex + schemeLength)

        protocol = URLProtocol.createOrDefault(scheme)
        startIndex += schemeLength + 1
    }

    // Auth & Host
    val slashCount = count(urlString, startIndex, endIndex, '/')
    startIndex += slashCount

    if (slashCount >= 2) {
        loop@ while (true) {
            val delimiter = urlString.indexOfAny("@/\\?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex

            if (delimiter < endIndex && urlString[delimiter] == '@') {
                // user and password check
                val passwordIndex = urlString.indexOfColonInHostPort(startIndex, delimiter)
                if (passwordIndex != -1) {
                    user = urlString.substring(startIndex, passwordIndex)
                    password = urlString.substring(passwordIndex + 1, delimiter)
                } else {
                    user = urlString.substring(startIndex, delimiter)
                }
                startIndex = delimiter + 1
            } else {
                fillHost(urlString, startIndex, delimiter)
                startIndex = delimiter
                break@loop
            }

        }
    }

    // Path
    encodedPath = "/"
    if (startIndex >= endIndex) return this
    val pathEnd = urlString.indexOfAny("?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex
    val rawPath = urlString.substring(startIndex, pathEnd)
    encodedPath = rawPath.encodeURLPath()
    startIndex = pathEnd

    // Query
    if (startIndex < endIndex && urlString[startIndex] == '?') {
        if (startIndex + 1 == endIndex) {
            trailingQuery = true
            return this
        }

        val fragmentStart = urlString.indexOf('#', startIndex + 1).takeIf { it > 0 } ?: endIndex

        val rawParameters = parseQueryString(urlString.substring(startIndex + 1, fragmentStart))
        rawParameters.forEach { key, values ->
            parameters.appendAll(key, values)
        }

        startIndex = fragmentStart
    }

    // Fragment
    if (startIndex < endIndex && urlString[startIndex] == '#') {
        fragment = urlString.substring(startIndex + 1, endIndex)
    }

    return this
}

private fun URLBuilder.fillHost(urlString: String, startIndex: Int, endIndex: Int) {
    val colonIndex = urlString.indexOfColonInHostPort(startIndex, endIndex).takeIf { it > 0 } ?: endIndex

    host = urlString.substring(startIndex, colonIndex)

    if (colonIndex + 1 < endIndex) {
        port = urlString.substring(colonIndex + 1, endIndex).toInt()
    }
}

private fun findScheme(urlString: String, startIndex: Int, endIndex: Int): Int {
    var current = startIndex
    while (current < endIndex) {
        if (urlString[current] == ':') return current

        ++current
    }
    return -1
}

private fun count(urlString: String, startIndex: Int, endIndex: Int, char: Char): Int {
    var result = 0
    while (startIndex + result < endIndex) {
        if (urlString[startIndex + result] != char) break
        result++
    }

    return result
}

private fun String.indexOfColonInHostPort(startIndex: Int, endIndex: Int): Int {
    var skip = false
    for (index in startIndex until endIndex) {
        when (this[index]) {
            '[' -> skip = true
            ']' -> skip = false
            ':' -> if (!skip) return index
        }
    }

    return -1
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

internal val ROOT_PATH = listOf("")

/**
 * Take url parts from [urlString]
 * throws [URLParserException]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.takeFrom)
 */
public fun URLBuilder.takeFrom(urlString: String): URLBuilder {
    if (urlString.isBlank()) return this

    return try {
        takeFromUnsafe(urlString)
    } catch (cause: Throwable) {
        throw URLParserException(urlString, cause)
    }
}

/**
 * Thrown when failed to parse URL
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLParserException)
 */
public class URLParserException(urlString: String, cause: Throwable) : IllegalStateException(
    "Fail to parse url: $urlString",
    cause
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

    // Special handling for data URLs
    if (protocol.name == "data") {
        host = urlString.substring(startIndex, endIndex)
        return this
    }

    // Auth & Host
    val slashCount = count(urlString, startIndex, endIndex, '/')
    startIndex += slashCount

    if (protocol.name == "file") {
        parseFile(urlString, startIndex, endIndex, slashCount)
        return this
    }

    if (protocol.name == "mailto") {
        require(slashCount == 0)
        parseMailto(urlString, startIndex, endIndex)
        return this
    }

    if (protocol.name == "about") {
        require(slashCount == 0)
        host = urlString.substring(startIndex, endIndex)
        return this
    }

    if (protocol.name == "tel") {
        require(slashCount == 0)
        host = urlString.substring(startIndex, endIndex)
        return this
    }

    if (slashCount >= 2) {
        loop@ while (true) {
            val delimiter = urlString.indexOfAny("@/\\?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex

            if (delimiter < endIndex && urlString[delimiter] == '@') {
                // user and password check
                val passwordIndex = urlString.indexOfColonInHostPort(startIndex, delimiter)
                if (passwordIndex != -1) {
                    encodedUser = urlString.substring(startIndex, passwordIndex)
                    encodedPassword = urlString.substring(passwordIndex + 1, delimiter)
                } else {
                    encodedUser = urlString.substring(startIndex, delimiter)
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
    if (startIndex >= endIndex) {
        encodedPathSegments = if (urlString[endIndex - 1] == '/') ROOT_PATH else emptyList()
        return this
    }

    val pathEnd = urlString.indexOfAny("?#".toCharArray(), startIndex).takeIf { it >= 0 } ?: endIndex
    if (pathEnd > startIndex) {
        encodedPathSegments = if (slashCount == 0) {
            // Relative path
            // last item is either file name or empty string for directories
            encodedPathSegments.dropLast(1)
        } else {
            emptyList()
        }

        val encodedPath = urlString.substring(startIndex, pathEnd).encodeURLPath(encodeEncoded = false)
        val basePath = when {
            encodedPathSegments.size == 1 && encodedPathSegments.first().isEmpty() -> emptyList()
            else -> encodedPathSegments
        }

        val encodedChunks = if (encodedPath == "/") ROOT_PATH else encodedPath.split('/')

        val relativePath = when (slashCount) {
            1 -> ROOT_PATH
            else -> emptyList()
        } + encodedChunks

        encodedPathSegments = basePath + relativePath
        startIndex = pathEnd
    } else if (slashCount == 0 && encodedPathSegments.isEmpty() && urlString[startIndex] == '?') {
        // Query-only references without a base path target the root resource.
        encodedPathSegments = ROOT_PATH
    } else if (slashCount > 0) {
        encodedPathSegments = emptyList()
    }

    // Query
    if (startIndex < endIndex && urlString[startIndex] == '?') {
        startIndex = parseQuery(urlString, startIndex, endIndex)
    }

    // Fragment
    parseFragment(urlString, startIndex, endIndex)
    return this
}

private fun URLBuilder.parseFile(urlString: String, startIndex: Int, endIndex: Int, slashCount: Int) {
    val pathStart = when (slashCount) {
        1 -> {
            host = ""
            startIndex
        }

        2 -> {
            val hostEnd = urlString.indexOfAny("/?#".toCharArray(), startIndex)
                .takeIf { it in startIndex until endIndex }
                ?: endIndex
            host = urlString.substring(startIndex, hostEnd)
            hostEnd
        }

        3 -> {
            host = ""
            startIndex
        }

        else -> throw IllegalArgumentException("Invalid file url: $urlString")
    }

    val pathEnd = urlString.indexOfAny("?#".toCharArray(), pathStart).takeIf { it >= 0 } ?: endIndex
    val path = urlString.substring(pathStart, pathEnd).encodeURLPath(encodeEncoded = false)
    encodedPath = if (slashCount == 3) "/$path" else path

    var nextPartStart = pathEnd
    if (nextPartStart < endIndex && urlString[nextPartStart] == '?') {
        nextPartStart = parseQuery(urlString, nextPartStart, endIndex)
    }
    parseFragment(urlString, nextPartStart, endIndex)
}

private fun URLBuilder.parseMailto(urlString: String, startIndex: Int, endIndex: Int) {
    val delimiter = urlString.indexOf("@", startIndex)
    if (delimiter == -1) {
        throw IllegalArgumentException("Invalid mailto url: $urlString, it should contain '@'.")
    }

    user = urlString.substring(startIndex, delimiter).decodeURLPart()
    host = urlString.substring(delimiter + 1, endIndex)
}

private fun URLBuilder.parseQuery(urlString: String, startIndex: Int, endIndex: Int): Int {
    if (startIndex + 1 == endIndex) {
        trailingQuery = true
        return endIndex
    }

    val fragmentStart = urlString.indexOf('#', startIndex + 1).takeIf { it > 0 } ?: endIndex

    val rawParameters = parseQueryString(urlString.substring(startIndex + 1, fragmentStart), decode = false)
    rawParameters.forEach { key, values ->
        encodedParameters.appendAll(key, values)
    }

    return fragmentStart
}

private fun URLBuilder.parseFragment(urlString: String, startIndex: Int, endIndex: Int) {
    if (startIndex < endIndex && urlString[startIndex] == '#') {
        encodedFragment = urlString.substring(startIndex + 1, endIndex)
    }
}

private fun URLBuilder.fillHost(urlString: String, startIndex: Int, endIndex: Int) {
    val colonIndex = urlString.indexOfColonInHostPort(startIndex, endIndex).takeIf { it > 0 } ?: endIndex

    host = urlString.substring(startIndex, colonIndex)

    port = if (colonIndex + 1 < endIndex) {
        urlString.substring(colonIndex + 1, endIndex).toInt()
    } else {
        DEFAULT_PORT
    }
}

/**
 * Finds scheme in the given [urlString]. If there is no scheme found the function returns -1. If the scheme contains
 * illegal characters an [IllegalArgumentException] will be thrown. If the scheme is present and it doesn't contain
 * illegal characters the function returns the length of the scheme.
 */
private fun findScheme(urlString: String, startIndex: Int, endIndex: Int): Int {
    var current = startIndex

    // Incorrect scheme position is used to identify the first position at which the character is not allowed in the
    // scheme or the part of the scheme. This number is reported in the exception message.
    var incorrectSchemePosition = -1
    val firstChar = urlString[current]
    if (firstChar !in 'a'..'z' && firstChar !in 'A'..'Z') {
        incorrectSchemePosition = current
    }

    while (current < endIndex) {
        val char = urlString[current]

        // Character ':' means the end of the scheme and at this point the length of the scheme should be returned or
        // the exception should be thrown in case the scheme contains illegal characters.
        if (char == ':') {
            if (incorrectSchemePosition != -1) {
                throw IllegalArgumentException("Illegal character in scheme at position $incorrectSchemePosition")
            }

            return current - startIndex
        }

        // If character '/' or '?' or '#' found this is not a scheme.
        if (char == '/' || char == '?' || char == '#') return -1

        // Update incorrect scheme position is current char is illegal.
        if (incorrectSchemePosition == -1 &&
            char !in 'a'..'z' &&
            char !in 'A'..'Z' &&
            char !in '0'..'9' &&
            char != '.' &&
            char != '+' &&
            char != '-'
        ) {
            incorrectSchemePosition = current
        }

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

private fun Char.isLetter(): Boolean = lowercaseChar() in 'a'..'z'

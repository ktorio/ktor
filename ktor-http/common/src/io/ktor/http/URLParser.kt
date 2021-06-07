/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Take url parts from [urlString]
 * throws [URLParserException]
 */
public fun URLBuilder.takeFrom(urlString: String): URLBuilder {
    return try {
        takeFromUnsafe(urlString)
    } catch (cause: Throwable) {
        throw URLParserException(urlString, cause)
    }
}

/**
 * Thrown when failed to parse URL
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

    if (slashCount >= 2) {
        loop@ while (true) {
            val delimiter = urlString.indexOfAny("@/\\?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex

            if (delimiter < endIndex && urlString[delimiter] == '@') {
                // user and password check
                val passwordIndex = urlString.indexOfColonInHostPort(startIndex, delimiter)
                if (passwordIndex != -1) {
                    user = urlString.substring(startIndex, passwordIndex).decodeURLPart()
                    password = urlString.substring(passwordIndex + 1, delimiter).decodeURLPart()
                } else {
                    user = urlString.substring(startIndex, delimiter).decodeURLPart()
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
        encodedPath = if (urlString[endIndex - 1] == '/') "/" else ""
        return this
    }

    encodedPath = if (slashCount == 0) {
        // Relative path
        val lastSlashIndex = encodedPath.lastIndexOf('/')

        if (lastSlashIndex != encodedPath.length - 1) {
            // Current path does not end in slash, get rid of last path segment.
            if (lastSlashIndex != -1) {
                encodedPath.substring(0, lastSlashIndex + 1)
            } else {
                "/"
            }
        } else {
            // keep the whole path
            encodedPath
        }
    } else {
        // overwrite the path
        ""
    }

    val pathEnd = urlString.indexOfAny("?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex
    val rawPath = urlString.substring(startIndex, pathEnd)

    encodedPath += rawPath.encodeURLPath()
    startIndex = pathEnd

    // Query
    if (startIndex < endIndex && urlString[startIndex] == '?') {
        startIndex = parseQuery(urlString, startIndex, endIndex)
    }

    // Fragment
    parseFragment(urlString, startIndex, endIndex)
    return this
}

private fun URLBuilder.parseFile(urlString: String, startIndex: Int, endIndex: Int, slashCount: Int) {
    when (slashCount) {
        2 -> {
            val nextSlash = urlString.indexOf('/', startIndex)
            if (nextSlash == -1 || nextSlash == endIndex) {
                host = urlString.substring(startIndex, endIndex)
                return
            }

            host = urlString.substring(startIndex, nextSlash)
            encodedPath = urlString.substring(nextSlash, endIndex)
        }
        3 -> {
            host = ""
            encodedPath = "/" + urlString.substring(startIndex, endIndex)
        }
        else -> throw IllegalArgumentException("Invalid file url: $urlString")
    }
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

    parseQueryStringTo(parameters, urlString.substring(startIndex + 1, fragmentStart))

    return fragmentStart
}

private fun URLBuilder.parseFragment(urlString: String, startIndex: Int, endIndex: Int) {
    if (startIndex < endIndex && urlString[startIndex] == '#') {
        fragment = urlString.substring(startIndex + 1, endIndex)
    }
}

private fun URLBuilder.fillHost(urlString: String, startIndex: Int, endIndex: Int) {
    val colonIndex = urlString.indexOfColonInHostPort(startIndex, endIndex).takeIf { it > 0 } ?: endIndex

    host = urlString.substring(startIndex, colonIndex)

    if (colonIndex + 1 < endIndex) {
        port = urlString.substring(colonIndex + 1, endIndex).toInt()
    } else {
        port = DEFAULT_PORT
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

private fun Char.isLetter(): Boolean = toLowerCase() in 'a'..'z'

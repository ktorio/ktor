/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

private const val SCHEME = """
        (?:(?<scheme>[a-zA-Z][\w.+-]*):(?!\d{1,4}))
    """
private const val USER_PASSWORD = """
        (?:
          (?<user>[^\/?#:]*)
            (?::(?<password>[^\/?#:]*)
          )?@
        )"""
private const val HOST = """
        (?:
          (?<host>
            (?:\[[^\]]+\])
            |(?:[^\/?#:]+[^\/?#:.])
          )
        )
    """
private const val PORT = """(?::(?<port>\d*))"""
private const val PATH = """(?<path>[^?#]*)"""
private const val QUERY = """(?<query>\?[^#]*)"""
private const val FRAGMENT = """(?:\#(?<fragment>.*))"""

/**
 * Utility for parsing URLs.
 *
 * The expressions used in parsing roughly map to https://www.rfc-editor.org/rfc/rfc3986#appendix-B
 * but are adopted with some leniency for easier use.
 *
 * There are two modes for parsing a URL used here:
 * 1. Parsing a fresh URL:
 *      - this assumes we have a host when the scheme is missing or otherwise ambiguous (i.e., localhost)
 * 2. Amending an existing URL:
 *      - this assumes we have a path in some similar situations (i.e., lib/utils)
 */
internal object UrlParser {

    private val initRegex =
        Regex("""
            \s*
            $SCHEME?
            (?:\/\/)?
            $USER_PASSWORD?
            $HOST?
            $PORT?
            $PATH
            $QUERY?
            $FRAGMENT?
            \s*
        """.removeWhitespace())

    private val appendRegex =
        Regex("""
            \s*
            (?:
              $SCHEME?
              (?:\/\/)
              $USER_PASSWORD?
              $HOST
              $PORT?
            )?
            $PATH
            $QUERY?
            $FRAGMENT?
            \s*
        """.removeWhitespace())

    internal fun URLBuilder.parse(urlString: String) {
        val match = (if (this.isEmpty()) initRegex else appendRegex).matchEntire(urlString)
            ?: throw IllegalArgumentException("Invalid URL: $urlString")

        match.withGroup("scheme") {
            protocol = URLProtocol.createOrDefault(it)
        }
        match.withGroup("user") {
            if (it.contains(' '))
                user = it
            else
                encodedUser = it
        }
        match.withGroup("password") {
            encodedPassword = it
        }
        match.withGroup("host") {
            host = it
            encodedPathSegments = emptyList()
        }
        match.withGroup("port") {
            port = it.toInt()
        }
        match.withGroup("path") { path ->
            // TODO normalize relative paths with ".." and "."
            if (path.startsWith('/')) {
                encodedPath = path
            // TODO dumb logic
            } else {
                encodedPathSegments =
                    (if (replaceLastSegment) encodedPathSegments.dropLast(1) else encodedPathSegments) +
                        path.split('/').dropWhile { it.isEmpty() }
            }

        }

        match.withGroup("query") {
            val queryString = it.trimStart('?')
            if (queryString.isEmpty()) {
                trailingQuery = true
            } else {
                parseQueryString(queryString, decode = false).forEach { key, values ->
                    encodedParameters.appendAll(key, values)
                }
            }
        }
        match.withGroup("fragment") {
            encodedFragment = it
        }
    }

    private fun MatchResult.withGroup(groupName: String, op: (String) -> Unit) {
        when (val group = groups[groupName]?.value) {
            null, "" -> return
            else -> op(group)
        }
    }

    private inline fun String.removeWhitespace() = filter { !it.isWhitespace() }
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

private const val PROTOCOL = """
    (?:(?<protocol>[a-zA-Z][\w.+-]*):(?!\d{1,4}))
"""
private const val PROTOCOL_SEPARATOR = """(?<separator>\/\/)"""
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
private const val QUERY = """(?:\?(?<query>[^#]*))"""
private const val FRAGMENT = """(?:\#(?<fragment>.*))"""
internal val USERNAME_CHARACTERS = ATTRIBUTE_CHARACTERS + setOf('%', ';', '\'')

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
internal object UriParser {

    private val uriReferenceRegex =
        Regex("""
            \s*
            $PROTOCOL?
            $PROTOCOL_SEPARATOR?
            (?<authority>
              $USER_PASSWORD?
              $HOST?
              $PORT?
            )
            $PATH
            $QUERY?
            $FRAGMENT?
            \s*
        """.removeWhitespace())

    private val relativeRegex =
        Regex("""
            \s*
            (?<authority>
              $PROTOCOL?
              $PROTOCOL_SEPARATOR
              $USER_PASSWORD?
              $HOST
              $PORT?
            )?
            $PATH
            $QUERY?
            $FRAGMENT?
            \s*
        """.removeWhitespace())

    internal fun parse(input: String, relative: Boolean = true): UriReference {
        val match = (if (relative) relativeRegex else uriReferenceRegex).matchEntire(input)
            ?: throw IllegalArgumentException("Invalid URL: $input")

        return UriReferenceParsed(match)
    }
}

/**
 * Represents a reference to a parsed URI, maintaining reference to the original string.
 */
public data class UriReferenceParsed(val match: MatchResult): EncodedUriReference {
    override val protocol: UrlProtocol?
        get() = match.withGroup("protocol") {
            UrlProtocol.createOrDefault(it)
        }
    override val protocolSeparator: ProtocolSeparator?
        get() = ProtocolSeparator.takeIf {
            match.hasGroup("separator") || !match.hasGroup("protocol")
        }
    override val port: Int? get() = match.withGroup("port") { it.toInt() }
    override val encodedHost: String? get() = match.withGroup("host")
    override val encodedUser: String? get() = match.withGroup("user")
    override val encodedPassword: String? get() = match.withGroup("password")
    override val encodedPath: String? get() = match.withGroup("path")
    override val encodedQuery: String? get() = match.withGroup("query")
    override val encodedFragment: String? get() = match.withGroup("fragment")

    override fun equals(other: Any?): Boolean = match.value == other.toString()
    override fun hashCode(): Int = match.value.hashCode()
    override fun toString(): String = match.value
}

private fun MatchResult.hasGroup(groupName: String): Boolean =
    groups[groupName]?.value !in setOf(null, "")

private fun MatchResult.withGroup(groupName: String): String? =
    withGroup(groupName) { it }

private fun <T> MatchResult.withGroup(groupName: String, op: (String) -> T): T? =
    when (val group = groups[groupName]?.value) {
        null, "" -> null
        else -> op(group)
    }

private inline fun String.removeWhitespace() = filter { !it.isWhitespace() }

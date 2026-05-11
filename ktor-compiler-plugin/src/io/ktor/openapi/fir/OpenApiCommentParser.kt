package io.ktor.openapi.fir

import io.ktor.openapi.Logger
import io.ktor.openapi.findJsonPrimitiveType
import io.ktor.openapi.routing.RouteFieldList
import io.ktor.openapi.routing.TypeReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.indexOfOrNull

internal val statusRegex = Regex("^\\d+$")
internal val contentTypeRegex = Regex("^(\\w+/\\S+)$")
internal val propertyLineRegex = Regex("^([\\w -_]+):\\s*(.*)$")
internal val propertySansColonRegex = Regex("^([\\w -_]+?)(\\W*)$")
internal val schemaArgRegex = Regex("^(:?)\\[(.*)]([?+]?)$")

/**
 * Parses the comment that precedes a given offset in the source text.
 * Handles both single-line (//) and block (/* */) comments.
 *
 * @param logger For logging errors
 * @param packageName The current package (for resolving type references)
 * @param text The source text of the file
 * @param beforeOffset The offset before which to look for comments
 * @return The extracted comment text or empty string if no comment is found
 */
fun parsePrecedingComment(
    logger: Logger,
    packageName: FqName,
    text: CharSequence,
    beforeOffset: Int
): RouteFieldList {
    // Ensure offset is within bounds
    val offset = beforeOffset.coerceIn(0, text.length)

    // Find the line start preceding the offset
    val lineStart = text.lastIndexOf('\n', offset - 1).let {
        if (it == -1) 0 else it + 1
    }

    // Get all text before the current line
    val precedingText = text.subSequence(0, lineStart)

    // Skip any whitespace immediately before the current line
    var currentPos = lineStart - 1
    while (currentPos >= 0 && text[currentPos].isWhitespace()) {
        currentPos--
    }

    // Check for single-line comments first
    val singleLineComments = mutableListOf<CommentLine>()
    var checkPos = currentPos

    while (checkPos >= 0) {
        // Find the start of the current line
        val currentLineStart = text.lastIndexOf('\n', checkPos).let {
            if (it == -1) 0 else it + 1
        }

        // Extract the current line
        val currentLine = text.substring(currentLineStart, checkPos + 1).trim()

        if (currentLine.startsWith("//")) {
            // Found a single-line comment
            singleLineComments.add(0, CommentLine(currentLine.substring(2).trim()))

            // Move to the previous line
            checkPos = currentLineStart - 2
        } else if (currentLine.isEmpty()) {
            // Skip empty lines
            checkPos = currentLineStart - 2
        } else {
            // Found a non-comment, non-empty line
            break
        }
    }

    // If we found single-line comments, return them
    if (singleLineComments.isNotEmpty()) {
        return if (singleLineComments.any { it.isKDocAttribute() }) {
            logger.log("KDoc annotation support is deprecated")
            singleLineComments.parseKDocParameters(logger, packageName)
        } else {
            parseMarkdownParameters(singleLineComments, logger, packageName)
        }
    }

    // Check for block comments
    val commentEnd = precedingText.lastIndexOf("*/")
    if (commentEnd != -1 && text.subSequence(commentEnd + 2, lineStart).isBlank()) {
        val commentStart = precedingText.lastIndexOf("/*", commentEnd)
        if (commentStart != -1) {
            val lines = precedingText.subSequence(commentStart + 2, commentEnd)
                .lines()
                .map(::CommentLine)
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) return emptyList()

            // Extract block comment content
            return if (lines.any { it.isKDocAttribute() }) {
                logger.log("KDoc annotation support is deprecated")
                lines.parseKDocParameters(logger, packageName)
            } else {
                parseMarkdownParameters(lines, logger, packageName)
            }
        }
    }

    // No comments found
    return emptyList()
}

data class CommentLine(
    private val rawText: String,
) {
    companion object {
        internal val propertyKeywords = setOf(
            "body",
            "cookie",
            "deprecated",
            "description",
            "externalDoc",
            "header",
            "ignore",
            "path",
            "path parameter",
            "query",
            "query parameter",
            "response",
            "security",
            "tag",
        )
    }
    private val startIndex: Int = (rawText.indexOfOrNull('*') ?: -1) + 1
    private val contentAfterStar: CharSequence get() = rawText.subSequence(startIndex, rawText.length)
    private val dashIndex: Int get() = contentAfterStar.indexOf('*')
        .takeIf { it > 0 && contentAfterStar.subSequence(0, it).isBlank() } ?: 0

    val content: CharSequence get() = contentAfterStar.trimStart()
    val trimmedContent: CharSequence get() = contentAfterStar.subSequence(dashIndex, contentAfterStar.length).trimStart('-', ' ')
    val indent: Int get() = contentAfterStar.indexOfFirst { !it.isWhitespace() }

    fun isNotEmpty(): Boolean = content.isNotBlank()
    fun isKDocAttribute(): Boolean = content.startsWith('@')

    fun isProperty(): Boolean =
        propertyLineRegex.matches(trimmedContent) ||
            propertySansColonRegex.matchEntire(trimmedContent)?.groupValues[1]?.lowercase() in propertyKeywords

    fun asPropertyMatch(): MatchResult? =
        propertyLineRegex.matchEntire(trimmedContent) ?:
            propertySansColonRegex.find(trimmedContent)

    fun appendTo(appendable: Appendable): Appendable = appendable.append(content).appendLine()
}

/**
 * Treats links like `[Type]?` as optional and `[Type]+` as arrays.
 *
 * This design may be revisited before release.
 */
internal fun getSchemaReference(
    prefix: String,
    name: String,
    postfix: String,
    packageName: FqName,
): TypeReference.Link {
    var link = when (val jsonType = findJsonPrimitiveType(name)) {
        null ->
            TypeReference.Link.Reference(
                if (name.contains('.')) name
                else "${packageName.asString()}.$name"
            )
        else -> TypeReference.Link.Primitive(name, jsonType)
    }
    link = when (postfix) {
        "?" -> TypeReference.Link.Optional(link)
        "+" -> TypeReference.Link.Array(link)
        else -> link
    }
    link = when (prefix) {
        ":" -> TypeReference.Link.Map(link)
        else -> link
    }
    return link
}
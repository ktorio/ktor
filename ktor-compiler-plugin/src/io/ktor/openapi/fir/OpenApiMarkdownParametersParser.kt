package io.ktor.openapi.fir

import io.ktor.openapi.*
import io.ktor.openapi.model.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField.*
import org.jetbrains.kotlin.name.FqName

internal fun parseMarkdownParameters(
    lines: List<CommentLine>,
    logger: Logger,
    packageName: FqName
): List<RouteField> {
    var i = 0

    // iterate through indented lines for attributes
    fun parseRouteField(
        property: String,
        content: CharSequence,
        indent: Int,
    ): RouteField? =
        parseParameter(
            packageName,
            logger,
            property,
            content,
            attributes = generateSequence {
                lines.getOrNull(i)?.takeIf {
                    it.isProperty() && it.indent > indent
                }?.also { i++ }?.parseAttribute(logger)
            }.toMap()
        )

    return buildList {
        // Add Summary from preamble
        val summary = buildString {
            while (i < lines.size && !lines[i].isProperty()) {
                appendLine(lines[i++].content)
            }
        }
        if (summary.isNotBlank()) {
            add(Summary(summary.trim()))
        }

        // Add remaining from properties
        while (i < lines.size) {
            val currentLine = lines[i++]
            val (property, content) = currentLine.asPropertyMatch()?.destructured ?: continue
            // A section of properties (responses, query parameters)
            if (property.isPlural() && content.isBlank()) {
                addAll(generateSequence {
                    lines.getOrNull(i)?.takeIf {
                        it.indent > currentLine.indent
                    }?.let { subLine ->
                        i++
                        parseRouteField(property, subLine.trimmedContent, subLine.indent)
                    }
                })
            }
            // A single property
            else parseRouteField(property, content, currentLine.indent)?.let(::add)
        }
    }
}

private fun String.isPlural() = endsWith("s")

private fun CommentLine.parseAttribute(logger: Logger): Pair<ModelAttribute, String>? {
    val (key, content) = asPropertyMatch()?.destructured ?: return null
    return when(val attr = ModelAttribute.parse(key)) {
        null -> {
            logger.log("Invalid attribute $key")
            null
        }
        else -> attr to content
    }
}

private fun parseParameter(
    packageName: FqName,
    logger: Logger,
    key: String,
    content: CharSequence,
    attributes: Map<ModelAttribute, String>,
): RouteField? {
    var wordIndex = 0
    val words = content.split(Regex("\\s+"))
    val next = { words.getOrNull(wordIndex++) ?: "null" }
    val nextReference = { LocalReference.StringValue(next()) }
    val tryMatchNext: Regex.() -> MatchResult? = {
        words.getOrNull(wordIndex)?.let { word ->
            matchEntire(word)?.let { match ->
                match.also { wordIndex++ }
            }
        }
    }
    val nextSchemaArg = {
        schemaArgRegex.tryMatchNext()?.let {
            val (prefix, name, postfix) = it.destructured
            getSchemaReference(prefix, name, postfix, packageName)
        }
    }
    val remaining = { words.drop(wordIndex).joinToString(" ").trim() }

    return when(key.lowercase().trimEnd('s')) {
        "body" -> Body(
            contentTypeRegex.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
            nextSchemaArg(),
            remaining(),
            attributes,
        )
        "cookie" -> Parameter(ParamIn.COOKIE, nextReference(), nextSchemaArg(), remaining(), attributes)
        "deprecated" -> RouteField.Deprecated(remaining())
        "description" -> Description(remaining())
        "externaldoc" -> ExternalDocs(next(), remaining())
        "header" -> Parameter(ParamIn.HEADER, nextReference(), nextSchemaArg(), remaining(), attributes)
        "ignore" -> Ignore
        "path",
        "path parameter" -> Parameter(ParamIn.PATH, nextReference(), nextSchemaArg(), remaining(), attributes)
        "query",
        "query parameter" -> Parameter(ParamIn.QUERY, nextReference(), nextSchemaArg(), remaining(), attributes)
        "response" -> Response(
            statusRegex.tryMatchNext()?.value?.toInt()?.let(LocalReference::of),
            contentTypeRegex.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
            nextSchemaArg(),
            remaining(),
            attributes,
        )
        "security" -> Security(next(), remaining().trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s*,\\s*")))
        "tag" -> Tag(next())
        "operationid" -> OperationId(next())
        else -> {
            logger.log("Unknown KDoc item: $key")
            null
        }
    }
}
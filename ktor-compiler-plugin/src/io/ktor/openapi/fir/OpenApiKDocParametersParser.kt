package io.ktor.openapi.fir

import io.ktor.openapi.*
import io.ktor.openapi.model.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField.*
import org.jetbrains.kotlin.name.FqName

@Deprecated("Replace with parseMarkdownParameters")
internal fun List<CommentLine>.parseKDocParameters(logger: Logger, packageName: FqName): List<RouteField> =
    sequence {
        val current = StringBuilder()
        for (line in this@parseKDocParameters) {
            if (line.isKDocAttribute()) {
                if (current.isNotEmpty()) {
                    yield(parseParameter(logger, packageName, current))
                    current.clear()
                }
            }
            line.appendTo(current)
        }
        if (current.isNotEmpty()) {
            yield(parseParameter(logger, packageName, current))
        }
    }.filterNotNull().toList()

private fun parseParameter(logger: Logger, packageName: FqName, text: CharSequence): RouteField? {
    try {
        if (!text.startsWith('@'))
            return Summary(text.toString().trim())
        val bodyAndAttributes = text.split(Regex("\n(?=\\s*\\$?\\p{Alpha}+)"), limit = 2)
        var i = 0
        val words = bodyAndAttributes.first().trim().removePrefix("@").split(Regex("\\s+"))
        val next = { words[i++] }
        val nextReference = { LocalReference.StringValue(next()) }
        val tryMatchNext: Regex.() -> MatchResult? = {
            words.getOrNull(i)?.let { word ->
                matchEntire(word)?.let { match ->
                    match.also { i++ }
                }
            }
        }
        val nextSchemaArg = {
            schemaArgRegex.tryMatchNext()?.let {
                val (prefix, name, postfix) = it.destructured
                getSchemaReference(prefix, name, postfix, packageName)
            }
        }
        val remaining = { words.drop(i).joinToString(" ").trim() }
        val attributes = {
            bodyAndAttributes.getOrNull(1)?.let {
                parseJsonSchemaAttributes(logger, it)
            } ?: emptyMap()
        }

        return when(val key = next()) {
            "body" -> Body(
                contentTypeRegex.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
                nextSchemaArg(),
                remaining(),
                attributes()
            )

            "cookie" -> Parameter(ParamIn.COOKIE, nextReference(), nextSchemaArg(), remaining(), attributes())
            "deprecated" -> RouteField.Deprecated(remaining())
            "description" -> Description(remaining())
            "externalDocs" -> ExternalDocs(next(), remaining())
            "header" -> Parameter(ParamIn.HEADER, nextReference(), nextSchemaArg(), remaining(), attributes())
            "ignore" -> Ignore
            "path" -> Parameter(ParamIn.PATH, nextReference(), nextSchemaArg(), remaining(), attributes())
            "query" -> Parameter(ParamIn.QUERY, nextReference(), nextSchemaArg(), remaining(), attributes())
            "response" -> Response(
                next().toIntOrNull()?.let(LocalReference::of),
                contentTypeRegex.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
                nextSchemaArg(),
                remaining(),
                attributes()
            )

            "security" -> Security(next(), remaining().trim().split(Regex("\\s*,\\s*")).ifEmpty { null })
            "tag" -> Tag(next())
            else -> {
                logger.log("Unknown KDoc item: @$key")
                null
            }
        }
    } catch (t: Throwable) {
        logger.log("Failed to parse parameter: $text", t)
        return null
    }
}

private fun parseJsonSchemaAttributes(logger: Logger, text: CharSequence): Map<ModelAttribute, String> =
    text.trim().lineSequence()
        .map { it.trim().split(Regex("\\s*:\\s*"), limit = 2) }
        .filter { it.size == 2 }
        .mapNotNull { (key, value) ->
            when(val attr = ModelAttribute.parse(key)) {
                null -> {
                    logger.log("Invalid attribute $key")
                    null
                }
                else -> attr to value
            }
        }
        .toMap()
package io.ktor.openapi.model

sealed interface ModelAttribute {
    companion object {
        fun parse(attributeKey: String): ModelAttribute? =
            if (attributeKey.startsWith("x-")) {
                ExtensionAttribute(attributeKey)
            } else {
                runCatching { SchemaAttribute.valueOf(attributeKey) }.getOrNull()
            }
    }

    val name: String
}

data class ExtensionAttribute(
    override val name: String
): ModelAttribute

@Suppress("EnumEntryName")
enum class SchemaAttribute: ModelAttribute {
    title,
    description,
    required,
    nullable,
    allOf,
    oneOf,
    not,
    anyOf,
    properties,
    additionalProperties,
    discriminator,
    readOnly,
    writeOnly,
    xml,
    externalDocs,
    example,
    examples,
    deprecated,
    maxProperties,
    minProperties,
    default,
    format,
    items,
    maximum,
    exclusiveMaximum,
    minimum,
    exclusiveMinimum,
    maxLength,
    minLength,
    pattern,
    maxItems,
    minItems,
    uniqueItems,
    enum,
    multipleOf,
    id,
    anchor,
    recursiveAnchor,
}
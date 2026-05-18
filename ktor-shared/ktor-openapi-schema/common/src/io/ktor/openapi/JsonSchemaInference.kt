/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.AdditionalProperties.*
import io.ktor.openapi.JsonSchema.*
import io.ktor.openapi.ReferenceOr.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Context interface for creating schema from type metadata.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonSchemaInference)
 */
public fun interface JsonSchemaInference {
    /**
     * Builds a [JsonSchema] for the given [type].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonSchemaInference.buildSchema)
     */
    public fun buildSchema(type: KType): JsonSchema
}

/**
 * Infers JSON schema from kotlinx-serialization descriptors.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.KotlinxJsonSchemaInference)
 */
public val KotlinxJsonSchemaInference: JsonSchemaInference get() = KotlinxSerializerJsonSchemaInference.Default

public val KotlinxSerializerDefaultFormats: (SerialDescriptor) -> String? = { type ->
    when (type.nonNullSerialName) {
        "kotlin.uuid.Uuid" -> "uuid"
        "kotlinx.datetime.LocalDate" -> "date"
        "kotlinx.datetime.LocalTime" -> "time"
        "kotlinx.datetime.TimeZone" -> "time-zone"
        "kotlinx.datetime.UtcOffset" -> "utc-offset"
        "kotlin.time.Instant",
        "kotlinx.datetime.Instant",
        "kotlinx.datetime.LocalDateTime" -> "date-time"
        "kotlin.time.Duration",
        "kotlinx.datetime.DatePeriod",
        "kotlinx.datetime.DateTimePeriod" -> "duration"
        else -> null
    }
}

/**
 * Infers JSON schema from kotlinx-serialization descriptors using the supplied module.
 *
 * @property module a [SerializersModule] to use for serialization.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.KotlinxSerializerJsonSchemaInference)
 */
public class KotlinxSerializerJsonSchemaInference(
    private val module: SerializersModule,
    private val formats: (SerialDescriptor) -> String? = KotlinxSerializerDefaultFormats,
) : JsonSchemaInference {
    public companion object {
        /**
         * Default instance of KotlinxSerializerJsonSchemaInference using an empty serializers module.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.KotlinxSerializerJsonSchemaInference.Companion.Default)
         */
        public val Default: KotlinxSerializerJsonSchemaInference =
            KotlinxSerializerJsonSchemaInference(EmptySerializersModule())
    }
    private val kTypeLookup = mutableMapOf<String, KType>()

    override fun buildSchema(type: KType): JsonSchema {
        includeKType(type)
        val serializer = module.serializer(type)
        return buildSchemaFromDescriptor(
            serializer.descriptor,
            // parameterized types cannot be referenced from their serial name
            includeTitle = type.arguments.isEmpty(),
            visiting = mutableSetOf(),
            serializer = serializer,
        )
    }

    private fun includeKType(type: KType) {
        // use toString() because qualifiedName is unavailable in web
        val qualifiedName = type.toString().substringBefore('<')
        if (qualifiedName in kTypeLookup) return
        kTypeLookup[qualifiedName] = type
        for (typeArg in type.arguments) {
            typeArg.type?.let(::includeKType)
        }
    }

    private fun SerialDescriptor.isParameterized(): Boolean =
        kTypeLookup[nonNullSerialName]?.arguments?.isNotEmpty() == true

    @OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
    internal fun buildSchemaFromDescriptor(
        descriptor: SerialDescriptor,
        includeTitle: Boolean = !descriptor.isParameterized(),
        includeAnnotations: List<Annotation> = emptyList(),
        visiting: MutableSet<String>,
        serializer: KSerializer<*>? = null,
        titleOverride: String? = null,
        visitingNameOverride: String? = null,
    ): JsonSchema {
        val reflectJsonSchema: KClass<*>.() -> ReferenceOr<JsonSchema> = {
            val serializer = module.serializer(this, emptyList(), false)
            val componentName = subclassComponentName(serializer)
            Value(
                buildSchemaFromDescriptor(
                    descriptor = serializer.descriptor,
                    includeTitle = includeTitle,
                    visiting = visiting,
                    serializer = serializer,
                    titleOverride = componentName,
                    visitingNameOverride = componentName,
                )
            )
        }
        val annotations = includeAnnotations + descriptor.annotations
        val isNullable = descriptor.isNullable

        // For inline descriptors, use the delegate descriptor
        if (descriptor.isInline) {
            return buildSchemaFromDescriptor(
                descriptor = descriptor.getElementDescriptor(0),
                includeAnnotations = includeAnnotations,
                visiting = visiting,
                serializer = nestedSerializerAt(serializer, 0),
                titleOverride = titleOverride,
                visitingNameOverride = visitingNameOverride,
            ).wrapIfNullable(isNullable)
        }

        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val visitingName = visitingNameOverride ?: descriptor.nonNullSerialName
                visiting += visitingName

                val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
                val required = mutableListOf<String>()

                for (i in 0 until descriptor.elementsCount) {
                    val name = descriptor.getElementName(i)
                    val elementDescriptor = descriptor.getElementDescriptor(i)
                    val childSerializer = nestedSerializerAt(serializer, i)
                    val elementReferenceName = referenceNameForChild(
                        parentDescriptor = descriptor,
                        childDescriptor = elementDescriptor,
                        parentReferenceName = visitingName,
                        childSerializer = childSerializer,
                    )
                    val annotations = descriptor.getElementAnnotations(i)
                    if (annotations.any { it is Ignore }) continue

                    if (!descriptor.isElementOptional(i) && !elementDescriptor.isNullable) {
                        required.add(name)
                    }

                    properties[name] = buildSchemaOrReference(
                        elementDescriptor,
                        elementReferenceName,
                        visiting,
                        descriptor.getElementAnnotations(i),
                        serializer = childSerializer,
                    )
                }
                visiting -= visitingName

                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT,
                    title = titleOverride ?: descriptor.nonNullSerialName.takeIf { includeTitle },
                    properties = properties,
                    required = required.takeIf { it.isNotEmpty() },
                ).wrapIfNullable(isNullable)
            }

            PolymorphicKind.SEALED -> {
                if (descriptor.elementsCount == 2) {
                    val discriminatorProperty = descriptor.annotations
                        .filterIsInstance<JsonClassDiscriminator>()
                        .firstOrNull()?.discriminator
                        ?: descriptor.getElementName(0)
                    val sealedElementsDescriptor = descriptor.getElementDescriptor(1)
                    val sealedSubclassComponentNames = sealedSubclassComponentNameMapping(serializer)
                    val sealedElementsSchema = (0..<sealedElementsDescriptor.elementsCount)
                        .map { i ->
                            val subclassDescriptor = sealedElementsDescriptor.getElementDescriptor(i)
                            val serialName = sealedElementsDescriptor.getElementName(i)
                            val subclassSerializer = nestedSerializerAt(serializer, i)
                            val componentName =
                                subclassComponentName(subclassSerializer)
                                    ?: sealedSubclassComponentNames[serialName]
                                    ?: fallbackSealedSubclassComponentName(
                                        descriptor = descriptor,
                                        subclassDescriptor = subclassDescriptor,
                                        serialName = serialName,
                                    )
                            buildSchemaOrReference(
                                descriptor = subclassDescriptor,
                                referenceName = componentName,
                                visiting = visiting,
                                annotations = sealedElementsDescriptor.getElementAnnotations(i),
                                serializer = subclassSerializer,
                                schemaTitleOverride = componentName,
                            )
                        }
                    val discriminatorMapping = (0..<sealedElementsDescriptor.elementsCount)
                        .associate { i ->
                            val serialName = sealedElementsDescriptor.getElementName(i)
                            val componentName = subclassComponentName(nestedSerializerAt(serializer, i))
                                ?: sealedSubclassComponentNames[serialName]
                                ?: fallbackSealedSubclassComponentName(
                                    descriptor = descriptor,
                                    subclassDescriptor = sealedElementsDescriptor.getElementDescriptor(i),
                                    serialName = serialName,
                                )
                            serialName to ReferenceOr.schema(componentName).ref
                        }

                    jsonSchemaFromAnnotations(
                        annotations = annotations,
                        reflectSchema = reflectJsonSchema,
                        type = JsonType.OBJECT.wrapIfNullable(isNullable),
                        title = descriptor.serialName.takeIf { includeTitle },
                        oneOf = sealedElementsSchema,
                        discriminator = JsonSchemaDiscriminator(discriminatorProperty, discriminatorMapping),
                    )
                } else {
                    jsonSchemaFromAnnotations(
                        annotations = annotations,
                        reflectSchema = reflectJsonSchema,
                        type = JsonType.OBJECT.wrapIfNullable(isNullable),
                        title = descriptor.serialName.takeIf { includeTitle },
                    )
                }
            }

            StructureKind.LIST -> {
                val itemDescriptor = descriptor.getElementDescriptor(0)
                val itemSchema = buildSchemaOrReference(
                    descriptor = itemDescriptor,
                    referenceName = referenceNameForChild(
                        parentDescriptor = descriptor,
                        childDescriptor = itemDescriptor,
                        parentReferenceName = visitingNameOverride ?: descriptor.nonNullSerialName,
                        childSerializer = nestedSerializerAt(serializer, 0),
                    ),
                    visiting = visiting,
                    serializer = nestedSerializerAt(serializer, 0),
                )
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.ARRAY.wrapIfNullable(isNullable),
                    items = itemSchema,
                )
            }

            StructureKind.MAP -> {
                val additionalProps = if (descriptor.elementsCount > 1) {
                    val valueDescriptor = descriptor.getElementDescriptor(1)
                    PSchema(
                        buildSchemaOrReference(
                            descriptor = valueDescriptor,
                            referenceName = referenceNameForChild(
                                parentDescriptor = descriptor,
                                childDescriptor = valueDescriptor,
                                parentReferenceName = visitingNameOverride ?: descriptor.nonNullSerialName,
                                childSerializer = nestedSerializerAt(serializer, 1),
                            ),
                            visiting = visiting,
                            serializer = nestedSerializerAt(serializer, 1),
                        )
                    )
                } else {
                    Allowed(true)
                }
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT.wrapIfNullable(isNullable),
                    additionalProperties = additionalProps,
                )
            }

            PrimitiveKind.STRING,
            PrimitiveKind.CHAR ->
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.STRING.wrapIfNullable(isNullable),
                    format = descriptor.takeIf { it.nonNullSerialName != "kotlin.String" }?.let(formats)
                )

            PrimitiveKind.BOOLEAN ->
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.BOOLEAN.wrapIfNullable(isNullable),
                )

            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.INTEGER.wrapIfNullable(isNullable),
                )

            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.NUMBER.wrapIfNullable(isNullable),
                )

            SerialKind.ENUM -> {
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.STRING.wrapIfNullable(isNullable),
                    enum = List(descriptor.elementsCount) { i -> GenericElement<String>(descriptor.getElementName(i)) },
                )
            }

            SerialKind.CONTEXTUAL -> {
                // For contextual serializers, resolve the actual serializer from the module
                // (if registered) and recurse into its descriptor so the schema reflects the
                // concrete representation chosen by the caller.
                val contextualDescriptor = descriptor.capturedKClass
                    ?.let { kClass -> module.getContextual(kClass)?.descriptor }
                if (contextualDescriptor != null) {
                    buildSchemaFromDescriptor(
                        descriptor = contextualDescriptor,
                        includeTitle = includeTitle,
                        includeAnnotations = includeAnnotations,
                        visiting = visiting,
                    ).wrapIfNullable(isNullable)
                } else {
                    jsonSchemaFromAnnotations(
                        annotations = annotations,
                        reflectSchema = reflectJsonSchema,
                        type = JsonType.OBJECT.wrapIfNullable(isNullable),
                    )
                }
            }

            PolymorphicKind.OPEN -> {
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT.wrapIfNullable(isNullable),
                )
            }
        }
    }

    /**
     * Generally builds a schema for the given type, unless it is already being processed, in which case a reference is
     * returned, so that we do not overflow the stack.
     */
    @OptIn(InternalAPI::class)
    private fun buildSchemaOrReference(
        descriptor: SerialDescriptor,
        referenceName: String,
        visiting: MutableSet<String>,
        annotations: List<Annotation> = emptyList(),
        serializer: KSerializer<*>? = null,
        schemaTitleOverride: String? = null,
    ): ReferenceOr<JsonSchema> =
        if (!descriptor.isContainerType() && !visiting.add(referenceName)) {
            ReferenceOr.schema(referenceName)
                .wrapIfNullable(descriptor.isNullable)
        } else {
            try {
                Value(
                    buildSchemaFromDescriptor(
                        descriptor = descriptor,
                        includeAnnotations = annotations,
                        visiting = visiting,
                        serializer = serializer,
                        titleOverride = schemaTitleOverride,
                        visitingNameOverride = referenceName,
                    )
                )
            } finally {
                visiting.remove(referenceName)
            }
        }

    private fun SerialDescriptor.isContainerType() =
        kind == StructureKind.LIST || kind == StructureKind.MAP

    private fun recursiveReferenceName(
        parentDescriptor: SerialDescriptor,
        childDescriptor: SerialDescriptor,
        parentReferenceName: String,
    ): String? = parentReferenceName.takeIf { parentDescriptor.nonNullSerialName == childDescriptor.nonNullSerialName }

    private fun referenceNameForChild(
        parentDescriptor: SerialDescriptor,
        childDescriptor: SerialDescriptor,
        parentReferenceName: String,
        childSerializer: KSerializer<*>? = null,
    ): String = subclassComponentName(childSerializer)
        ?: recursiveReferenceName(
            parentDescriptor = parentDescriptor,
            childDescriptor = childDescriptor,
            parentReferenceName = parentReferenceName,
        )
        ?: childDescriptor.nonNullSerialName

    private fun fallbackSealedSubclassComponentName(
        descriptor: SerialDescriptor,
        subclassDescriptor: SerialDescriptor,
        serialName: String,
    ): String {
        val subclassName = subclassDescriptor.nonNullSerialName
        return if (subclassName == serialName) {
            "${descriptor.nonNullSerialName}.$serialName"
        } else {
            subclassName
        }
    }
}

/**
 * Generates a JSON Schema representation from a Kotlinx Serialization [SerialDescriptor].
 *
 * Supports the following descriptor kinds:
 * - CLASS/OBJECT: Maps to object schema with properties and required fields (based on nullability)
 * - LIST: Maps to array schema with items
 * - MAP: Maps to object schema with additionalProperties
 * - Primitives (STRING, BOOLEAN, INT, LONG, FLOAT, DOUBLE, etc.): Maps to corresponding JSON types
 * - ENUM: Maps to string schema with enum values
 * - CONTEXTUAL: Returns a generic object schema (actual type resolution requires serialization context)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.buildJsonSchema)
 *
 * @return A [JsonSchema] object representing the JSON Schema for this descriptor.
 *
 * Note: This function does not handle circular references. For types with circular dependencies,
 * consider implementing depth tracking or schema references to avoid stack overflow.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class, InternalAPI::class)
public fun SerialDescriptor.buildJsonSchema(
    includeTitle: Boolean = true,
    includeAnnotations: List<Annotation> = emptyList(),
    visiting: MutableSet<String>,
): JsonSchema =
    KotlinxSerializerJsonSchemaInference.Default.buildSchemaFromDescriptor(
        this,
        includeTitle,
        includeAnnotations,
        visiting
    )

private val SerialDescriptor.nonNullSerialName get() = serialName.trimEnd('?')

@InternalAPI
public fun ReferenceOr<JsonSchema>.wrapIfNullable(isNullable: Boolean): ReferenceOr<JsonSchema> =
    if (isNullable) {
        Value(
            JsonSchema(
                oneOf = listOf(
                    this,
                    Value(JsonSchema(type = JsonType.NULL))
                )
            )
        )
    } else {
        this
    }

@InternalAPI
public fun JsonSchema.wrapIfNullable(isNullable: Boolean): JsonSchema =
    if (!isNullable) {
        this
    } else {
        when (val schemaType = type) {
            is JsonType -> copy(type = schemaType.wrapIfNullable(true))
            is SchemaType.AnyOf -> if (JsonType.NULL in schemaType.types) {
                this
            } else {
                copy(type = SchemaType.AnyOf(schemaType.types + JsonType.NULL))
            }
            null -> JsonSchema(
                oneOf = listOf(
                    Value(this),
                    Value(JsonSchema(type = JsonType.NULL))
                )
            )
        }
    }

@InternalAPI
public fun JsonType.wrapIfNullable(isNullable: Boolean): SchemaType =
    if (isNullable) SchemaType.AnyOf(listOf(this, JsonType.NULL)) else this

/**
 * Create an instance of JsonSchema from the provided properties and supplied annotations.
 *
 * This is used internally for different inference strategies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.jsonSchemaFromAnnotations)
 */
@InternalAPI
public fun jsonSchemaFromAnnotations(
    annotations: List<Annotation>,
    reflectSchema: (KClass<*>).() -> ReferenceOr<JsonSchema>,
    type: SchemaType,
    title: String? = null,
    required: List<String>? = null,
    items: ReferenceOr<JsonSchema>? = null,
    properties: Map<String, ReferenceOr<JsonSchema>>? = null,
    additionalProperties: AdditionalProperties? = null,
    enum: List<GenericElement?>? = null,
    format: String? = null,
    discriminator: JsonSchemaDiscriminator? = null,
    oneOf: List<ReferenceOr<JsonSchema>>? = null,
): JsonSchema {
    fun parseJsonLiteralToGenericElement(text: String): GenericElement {
        val element: JsonElement = Json.parseToJsonElement(text)
        return JsonGenericElement(element, Json, JsonElement.serializer())
    }

    fun parseJsonLiteralOrUseString(text: String): GenericElement? =
        try {
            val element = Json.parseToJsonElement(text)
            JsonGenericElement(element, Json, JsonElement.serializer())
        } catch (_: Exception) {
            GenericElement(text)
        }

    fun refFromString(raw: String): ReferenceOr<JsonSchema> {
        val trimmed = raw.trim()
        // If caller passed a full JSON-pointer-ish ref, keep it; otherwise treat it as a schema component name.
        return if (trimmed.startsWith("#/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Reference(trimmed)
        } else {
            ReferenceOr.schema(trimmed)
        }
    }

    return JsonSchema(
        type = annotations.firstInstanceOf<Type>()?.value ?: type,
        title = annotations.firstInstanceOf<Title>()?.value ?: title,
        description = annotations.firstInstanceOf<Description>()?.value,
        required =
        annotations.firstInstanceOf<JsonSchema.Required>()?.value?.toList()?.takeIf { it.isNotEmpty() }
            ?: required,
        anyOf = annotations.firstInstanceOf<AnyOfRefs>()?.value
            ?.map { refFromString(it) }
            ?.takeIf { it.isNotEmpty() },
        oneOf = annotations.firstInstanceOf<OneOf>()?.value
            ?.map { it.reflectSchema() }
            ?.takeIf { it.isNotEmpty() } ?: oneOf,
        not = annotations.firstInstanceOf<Not>()?.value?.reflectSchema(),
        properties = properties,
        additionalProperties =
        annotations.firstInstanceOf<AdditionalPropertiesRef>()?.value?.let { PSchema(it.reflectSchema()) }
            ?: annotations.firstInstanceOf<AdditionalPropertiesAllowed>()?.let { Allowed(true) }
            ?: additionalProperties,
        discriminator = annotations.firstInstanceOf<Discriminator>()?.let { annotation ->
            JsonSchemaDiscriminator(
                annotation.property,
                annotation.mapping.associate {
                    it.key to "#/components/schemas/${typeName(it)}"
                }
            )
        } ?: discriminator,
        readOnly = annotations.firstInstanceOf<ReadOnly>()?.let { true },
        writeOnly = annotations.firstInstanceOf<WriteOnly>()?.let { true },
        deprecated = annotations.firstInstanceOf<DeprecatedSchema>()?.let { true },
        maxProperties = annotations.firstInstanceOf<MaxProperties>()?.value,
        minProperties = annotations.firstInstanceOf<MinProperties>()?.value,
        default = annotations.firstInstanceOf<Default>()?.value?.let { parseJsonLiteralToGenericElement(it) },
        format = annotations.firstInstanceOf<Format>()?.value ?: format,
        items = annotations.firstInstanceOf<ItemsRef>()?.value?.reflectSchema() ?: items,
        prefixItems = annotations.firstInstanceOf<PrefixItemsRef>()?.value
            ?.map { it.reflectSchema() }
            ?.takeIf { it.isNotEmpty() },
        maximum = annotations.firstInstanceOf<Maximum>()?.takeIf { !it.exclusive }?.value,
        exclusiveMaximum = annotations.firstInstanceOf<Maximum>()?.takeIf { it.exclusive }?.value,
        minimum = annotations.firstInstanceOf<Minimum>()?.takeIf { !it.exclusive }?.value,
        exclusiveMinimum = annotations.firstInstanceOf<Minimum>()?.takeIf { it.exclusive }?.value,
        maxLength = annotations.firstInstanceOf<MaxLength>()?.value,
        minLength = annotations.firstInstanceOf<MinLength>()?.value,
        pattern = annotations.firstInstanceOf<Pattern>()?.value,
        maxItems = annotations.firstInstanceOf<MaxItems>()?.value,
        minItems = annotations.firstInstanceOf<MinItems>()?.value,
        uniqueItems = annotations.firstInstanceOf<UniqueItems>()?.let { true },
        enum = annotations.firstInstanceOf<JsonSchema.Enum>()?.value
            ?.map { parseJsonLiteralOrUseString(it) }
            ?.takeIf { it.isNotEmpty() } ?: enum,
        multipleOf = annotations.firstInstanceOf<MultipleOf>()?.value,
        id = annotations.firstInstanceOf<Id>()?.value,
        anchor = annotations.firstInstanceOf<Anchor>()?.value,
        dynamicAnchor = annotations.firstInstanceOf<Anchor>()?.dynamic?.takeIf { it },
        example = annotations.firstInstanceOf<Example>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.singleOrNull(),
        examples = annotations.firstInstanceOf<Example>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.takeIf { it.size > 1 },
    )
}

internal expect fun typeName(mapping: Discriminator.Mapping): String?
internal expect fun sealedSubclassComponentNameMapping(serializer: KSerializer<*>?): Map<String, String>
internal expect fun nestedSerializerAt(serializer: KSerializer<*>?, index: Int): KSerializer<*>?
internal expect fun subclassComponentName(serializer: KSerializer<*>?): String?

private inline fun <reified T : Annotation> List<Annotation>.firstInstanceOf(): T? =
    filterIsInstance<T>().firstOrNull()

/**
 * Generates a JSON Schema representation for the given type [T].
 *
 * This function attempts to infer the schema from the type's [KSerializer].
 * If the type is not serializable, returns a Schema representing a non-serializable type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.jsonSchema)
 *
 * @return A [JsonSchema] object representing the JSON Schema for type [T].
 */
public inline fun <reified T : Any> JsonSchemaInference.jsonSchema(): JsonSchema {
    return try {
        buildSchema(typeOf<T>())
    } catch (e: SerializationException) {
        JsonSchema(
            type = JsonType.OBJECT,
            description = "Failed to resolve schema for ${T::class.simpleName}. ${e::class.simpleName}: ${e.message}"
        )
    }
}

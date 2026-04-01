/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi.reflect

import io.ktor.openapi.*
import io.ktor.utils.io.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An adapter used by [ReflectionJsonSchemaInference] to customize how Kotlin types and properties
 * are mapped to OpenAPI schema components.
 *
 * This interface allows overriding the default reflection behavior, such as changing property names,
 * filtering ignored fields, or handling specific nullability rules.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter)
 */
public interface SchemaReflectionAdapter {

    /**
     * Provides a name for the given [type] to be used as a title in the JSON schema.
     * By default, returns the qualified name for non-generic classes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter.getName)
     */
    public fun getName(type: KType): String? =
        if (type.arguments.isNotEmpty()) {
            null
        } else {
            (type.classifier as? KClass<*>)?.qualifiedName
        }

    /**
     * Returns the collection of properties for a given [kClass] that should be included in the schema.
     * By default, returns all member properties.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter.getProperties)
     */
    public fun <T : Any> getProperties(kClass: KClass<T>): Collection<KProperty1<T, *>> =
        kClass.memberProperties

    /**
     * Returns the schema property name for the given [property].
     * By default, returns the Kotlin property name.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter.getName)
     */
    public fun getName(property: KProperty1<*, *>): String =
        property.name

    /**
     * Determines if the given [property] should be excluded from the generated schema.
     * By default, ignores properties annotated with [JsonSchema.Ignore].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter.isIgnored)
     */
    public fun isIgnored(property: KProperty1<*, *>): Boolean =
        property.annotations.any { it is JsonSchema.Ignore }

    /**
     * Determines if the given [type] should be marked as nullable in the OpenAPI schema.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.SchemaReflectionAdapter.isNullable)
     */
    public fun isNullable(type: KType): Boolean =
        type.isMarkedNullable
}

/**
 * Infers JSON schema from reflection.
 *
 * Designed for JVM servers where other frameworks (Jackson/Gson/etc.) may control JSON shape.
 * This implementation can be extended by providing custom [adapter].
 *
 * Notes / limitations:
 * - No $ref/component extraction is done here (schemas are expanded inline).
 * - Cycles are guarded to avoid stack overflows, but will degrade to a generic OBJECT schema.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.ReflectionJsonSchemaInference)
 */
public class ReflectionJsonSchemaInference(
    private val adapter: SchemaReflectionAdapter
) : JsonSchemaInference {
    public companion object {
        public val Default: JsonSchemaInference = ReflectionJsonSchemaInference(object : SchemaReflectionAdapter {})
    }

    override fun buildSchema(type: KType): JsonSchema =
        buildSchemaInternal(type, LinkedHashSet())

    /**
     * Creates an object schema for [kClass] with properties inferred from Kotlin reflection.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.reflect.ReflectionJsonSchemaInference.schemaForClass)
     */
    public fun schemaForClass(kClass: KClass<*>): JsonSchema {
        return buildSchemaInternal(
            kClass.starProjectedTypeOrNull() ?: return JsonSchema(type = JsonType.OBJECT),
            LinkedHashSet()
        )
    }

    public fun schemaRefForClass(kClass: KClass<*>): ReferenceOr<JsonSchema> =
        ReferenceOr.Value(schemaForClass(kClass))

    // ----------------------------
    // Implementation
    // ----------------------------

    @OptIn(InternalAPI::class)
    private fun buildSchemaInternal(
        type: KType,
        visiting: MutableSet<String>,
        includeAnnotations: List<Annotation> = emptyList()
    ): JsonSchema {
        val typeName = adapter.getName(type)?.also(visiting::add)
        try {
            val kClass = type.classifier as? KClass<*>
                ?: return JsonSchema(type = JsonType.OBJECT)

            // Nullability: OpenAPI schema has a `nullable` flag
            val nullable = adapter.isNullable(type)

            // Primitives / common JDK types
            val primitiveSchema = primitiveSchemaOrNull(kClass, includeAnnotations, nullable)
            if (primitiveSchema != null) {
                return primitiveSchema
            }

            // Value classes (inline) should be represented as their underlying value
            if (kClass.isValue) {
                kClass.underlyingValueClassTypeOrNull()?.let { underlyingType ->
                    val unboxedSchema = buildSchemaInternal(
                        underlyingType,
                        visiting,
                        includeAnnotations + kClass.annotations
                    )
                    return unboxedSchema.nonNullable(nullable)
                }
            }

            // Enums
            if (kClass.java.isEnum) {
                val values = kClass.java.enumConstants
                    ?.map { it.toString() }
                    .orEmpty()
                    .map { GenericElement<String>(it) }

                return jsonSchemaFromAnnotations(
                    annotations = includeAnnotations + kClass.annotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.STRING.orNullable(nullable),
                    enum = values,
                )
            }

            // Sealed classes
            if (kClass.isSealed) {
                val sealedSubclasses = kClass.sealedSubclasses
                val sealedSubclassSchema = sealedSubclasses.map {
                    ReferenceOr.Value(buildSchemaInternal(it.starProjectedType, visiting))
                }
                val discriminatorMapping = sealedSubclasses
                    .filter { it.qualifiedName != null && it.simpleName != null }
                    .associate { subclass ->
                        subclass.qualifiedName!! to "#/components/schemas/${subclass.simpleName}"
                    }

                return jsonSchemaFromAnnotations(
                    title = adapter.getName(type),
                    annotations = includeAnnotations + kClass.annotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.OBJECT.orNullable(nullable),
                    oneOf = sealedSubclassSchema,
                    discriminator = JsonSchemaDiscriminator("type", discriminatorMapping),
                )
            }

            // Arrays / Iterables
            if (kClass == Array<Any>::class || kClass.java.isArray || kClass.isSubclassOf(Iterable::class)) {
                val itemType = type.arguments.firstOrNull()?.type
                val itemSchema = itemType?.let { buildSchemaInternal(it, visiting) }
                    ?: JsonSchema(type = JsonType.OBJECT)

                return jsonSchemaFromAnnotations(
                    annotations = includeAnnotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.ARRAY.orNullable(nullable),
                    items = ReferenceOr.Value(itemSchema),
                )
            }

            // Map -> object with additionalProperties
            if (kClass.isSubclassOf(Map::class)) {
                // key type ignored
                val valueType = type.arguments.getOrNull(1)?.type

                // JSON object keys are strings; if key isn't String, we still produce an object schema.
                val additional = valueType?.let { v ->
                    AdditionalProperties.PSchema(ReferenceOr.Value(buildSchemaInternal(v, visiting)))
                } ?: AdditionalProperties.Allowed(true)

                return jsonSchemaFromAnnotations(
                    annotations = includeAnnotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.OBJECT.orNullable(nullable),
                    additionalProperties = additional,
                )
            }

            val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
            val required = mutableListOf<String>()

            for (prop in adapter.getProperties(kClass)) {
                if (adapter.isIgnored(prop)) continue

                val propertyName = adapter.getName(prop)
                val typeName = adapter.getName(prop.returnType)
                val propertyIsNullable = adapter.isNullable(prop.returnType)

                properties[propertyName] = if (typeName != null && !visiting.add(typeName)) {
                    ReferenceOr.schema(typeName).nonNullable(propertyIsNullable)
                } else {
                    val propSchema = buildSchemaInternal(prop.returnType, visiting, prop.annotations)
                    ReferenceOr.Value(propSchema)
                }

                // Required: non-nullable properties are required (best effort; default values are not detectable reliably)
                if (!propertyIsNullable) {
                    required += propertyName
                }
            }

            return jsonSchemaFromAnnotations(
                title = adapter.getName(type),
                annotations = includeAnnotations + kClass.annotations,
                reflectSchema = ::schemaRefForClass,
                type = JsonType.OBJECT,
                properties = properties.takeIf { it.isNotEmpty() },
                required = required.takeIf { it.isNotEmpty() },
            ).nonNullable(nullable)
        } finally {
            if (typeName != null) {
                visiting.remove(typeName)
            }
        }
    }

    @OptIn(
        ExperimentalTime::class,
        ExperimentalUuidApi::class,
        InternalAPI::class,
    )
    private fun primitiveSchemaOrNull(
        kClass: KClass<*>,
        annotations: List<Annotation>,
        nullable: Boolean
    ): JsonSchema? = when (kClass) {
        String::class, Char::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable)
        )

        Boolean::class -> jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.BOOLEAN)

        Byte::class, Short::class, Int::class, Long::class,
        UByte::class, UShort::class, UInt::class, ULong::class,
        java.lang.Byte::class, java.lang.Short::class, Integer::class, java.lang.Long::class ->
            jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.INTEGER)

        Float::class, Double::class,
        java.lang.Float::class, java.lang.Double::class ->
            jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.NUMBER)

        Uuid::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "uuid"
        )

        java.time.Instant::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date-time"
        )

        OffsetDateTime::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date-time"
        )

        java.time.LocalDate::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date"
        )

        java.time.LocalDateTime::class,
        kotlinx.datetime.Instant::class,
        Instant::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date-time"
        )

        LocalDate::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date"
        )

        LocalDateTime::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING.orNullable(nullable),
            format = "date-time"
        )

        Duration::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "duration"
        )

        else -> null
    }

    private fun KClass<*>.underlyingValueClassTypeOrNull(): KType? {
        val ctorParam = primaryConstructor?.parameters?.singleOrNull()
            ?: return null

        // Prefer the backing property type when available (better chance of having resolved type args)
        val propType = memberProperties.firstOrNull { it.name == ctorParam.name }?.returnType
        return propType ?: ctorParam.type
    }

    private fun KClass<*>.starProjectedTypeOrNull(): KType? = try {
        @Suppress("UNCHECKED_CAST")
        (this as KClass<Any>).starProjectedType
    } catch (_: Exception) {
        null
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

public interface SchemaReflectionAdapter {

    public fun getName(type: KType): String? =
        if (type.arguments.isNotEmpty()) {
            null
        } else {
            (type.classifier as? KClass<*>)?.qualifiedName
        }

    public fun <T : Any> getProperties(kClass: KClass<T>): Collection<KProperty1<T, *>> =
        kClass.memberProperties

    public fun getName(property: KProperty1<*, *>): String =
        property.name

    public fun isIgnored(property: KProperty1<*, *>): Boolean =
        property.annotations.any { it is JsonSchema.Annotations.Ignore }

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

    private fun buildSchemaInternal(
        type: KType,
        visiting: MutableSet<KType>,
        includeAnnotations: List<Annotation> = emptyList()
    ): JsonSchema {
        // Cycle guard (best-effort): if we see the same type again, stop expanding.
        if (!visiting.add(type)) {
            return JsonSchema(
                type = JsonType.OBJECT,
                title = adapter.getName(type),
                description = "Recursive type encountered; schema expansion stopped to prevent cycles."
            )
        }

        try {
            val kClass = type.classifier as? KClass<*>
                ?: return JsonSchema(type = JsonType.OBJECT)

            // Nullability: OpenAPI schema has a `nullable` flag
            val nullable = adapter.isNullable(type)

            // Primitives / common JDK types
            val primitiveSchema = primitiveSchemaOrNull(kClass, includeAnnotations)
            if (primitiveSchema != null) {
                return if (nullable) {
                    primitiveSchema.copy(nullable = true)
                } else {
                    primitiveSchema
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
                    type = JsonType.STRING,
                    enum = values,
                    nullable = nullable,
                )
            }

            // Sealed classes
            if (kClass.isSealed) {
                val sealedSubclasses = kClass.sealedSubclasses
                val mapping = sealedSubclasses.associate { subclass ->
                    subclass.qualifiedName!! to "#/components/schemas/${subclass.simpleName}"
                }

                return jsonSchemaFromAnnotations(
                    title = adapter.getName(type),
                    annotations = includeAnnotations + kClass.annotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.OBJECT,
                    discriminator = JsonSchema.Discriminator("type", mapping),
                    nullable = nullable
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
                    type = JsonType.ARRAY,
                    items = ReferenceOr.Value(itemSchema),
                    nullable = nullable
                )
            }

            // Map -> object with additionalProperties
            if (kClass.isSubclassOf(Map::class)) {
                // key type ignod
                val valueType = type.arguments.getOrNull(1)?.type

                // JSON object keys are strings; if key isn't String, we still produce an object schema.
                val additional = valueType?.let { v ->
                    AdditionalProperties.PSchema(ReferenceOr.Value(buildSchemaInternal(v, visiting)))
                } ?: AdditionalProperties.Allowed(true)

                return jsonSchemaFromAnnotations(
                    annotations = includeAnnotations,
                    reflectSchema = ::schemaRefForClass,
                    type = JsonType.OBJECT,
                    additionalProperties = additional,
                    nullable = nullable
                )
            }

            // Fallback: treat as object (data classes, POJOs, etc.)
            val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
            val required = mutableListOf<String>()

            for (prop in adapter.getProperties(kClass)) {
                if (adapter.isIgnored(prop)) continue

                val name = adapter.getName(prop)
                val propSchema = buildSchemaInternal(prop.returnType, visiting, prop.annotations)

                properties[name] = ReferenceOr.Value(propSchema)

                // Required: non-nullable properties are required (best effort; default values are not detectable reliably)
                if (!prop.returnType.isMarkedNullable) {
                    required += name
                }
            }

            return jsonSchemaFromAnnotations(
                title = adapter.getName(type),
                annotations = includeAnnotations + kClass.annotations,
                reflectSchema = ::schemaRefForClass,
                type = JsonType.OBJECT,
                properties = properties.takeIf { it.isNotEmpty() },
                required = required.takeIf { it.isNotEmpty() },
                nullable = nullable
            )
        } finally {
            visiting.remove(type)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun primitiveSchemaOrNull(kClass: KClass<*>, annotations: List<Annotation>): JsonSchema? = when (kClass) {
        String::class, Char::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING
        )
        Boolean::class -> jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.BOOLEAN)

        Byte::class, Short::class, Int::class, Long::class,
        java.lang.Byte::class, java.lang.Short::class, Integer::class, java.lang.Long::class ->
            jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.INTEGER)

        Float::class, Double::class,
        java.lang.Float::class, java.lang.Double::class ->
            jsonSchemaFromAnnotations(annotations, ::schemaRefForClass, type = JsonType.NUMBER)

        // Java time
        java.time.Instant::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date-time"
        )
        OffsetDateTime::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date-time"
        )
        java.time.LocalDate::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date"
        )
        java.time.LocalDateTime::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date-time"
        )

        // Kotlinx datetime
        Instant::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date-time"
        )
        LocalDate::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date"
        )
        LocalDateTime::class -> jsonSchemaFromAnnotations(
            annotations,
            ::schemaRefForClass,
            type = JsonType.STRING,
            format = "date-time"
        )

        else -> null
    }

    private fun KClass<*>.starProjectedTypeOrNull(): KType? = try {
        @Suppress("UNCHECKED_CAST")
        (this as KClass<Any>).starProjectedType
    } catch (_: Throwable) {
        null
    }
}

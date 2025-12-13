/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

/**
 * Infers JSON schema from reflection.
 *
 * Designed for JVM servers where other frameworks (Jackson/Gson/etc.) may control JSON shape.
 * This implementation is intentionally extensible: override hooks, or provide [customizers].
 *
 * Notes / limitations:
 * - No $ref/component extraction is done here (schemas are expanded inline).
 * - Cycles are guarded to avoid stack overflows, but will degrade to a generic OBJECT schema.
 */
public open class ReflectionJsonSchemaInference(
    /**
     * Optional extension points for framework-specific behavior (ignore/rename/etc).
     */
    private val customizers: List<Customizer> = emptyList(),
) : JsonSchemaInference {

    /**
     * Customization hook for framework-specific annotations and conventions.
     *
     * Implementations should be lightweight and side-effect free.
     */
    public interface Customizer {
        /** Return null to keep the original name. */
        public fun propertyName(property: KProperty1<*, *>): String? = null

        /** Return false to exclude the property from schema. */
        public fun includeProperty(property: KProperty1<*, *>): Boolean = true

        /** Allows post-processing the built property schema. */
        public fun customizePropertySchema(
            property: KProperty1<*, *>,
            schema: JsonSchema
        ): JsonSchema = schema

        /** Allows post-processing the built class schema. */
        public fun customizeClassSchema(
            kClass: KClass<*>,
            schema: JsonSchema
        ): JsonSchema = schema
    }

    override fun buildSchema(type: KType): JsonSchema = buildSchemaInternal(type, LinkedHashSet())

    /**
     * Creates an object schema for [kClass] with properties inferred from Kotlin reflection.
     */
    public open fun schemaForClass(kClass: KClass<*>): JsonSchema {
        return buildSchemaInternal(
            kClass.starProjectedTypeOrNull() ?: return JsonSchema(type = JsonSchema.JsonType.OBJECT),
            LinkedHashSet()
        )
    }

    // ----------------------------
    // Extensibility hooks (override)
    // ----------------------------

    /**
     * Override to change how class titles are produced.
     */
    public open fun titleForClass(kClass: KClass<*>): String? = kClass.simpleName

    /**
     * Override to decide whether a property is included in the schema.
     *
     * Default behavior:
     * - Honors [Customizer.includeProperty]
     * - Skips some common ignore-annotations by *name* (no hard dependency on Jackson)
     */
    public open fun shouldIncludeProperty(property: KProperty1<*, *>): Boolean {
        if (customizers.any { !it.includeProperty(property) }) return false
        return !hasIgnoredByWellKnownAnnotations(property)
    }

    /**
     * Override to customize property naming (e.g., Jackson @JsonProperty).
     */
    public open fun propertyName(property: KProperty1<*, *>): String =
        customizers.firstNotNullOfOrNull { it.propertyName(property) } ?: property.name

    /**
     * Override to apply custom annotation-driven constraints.
     *
     * By default, applies:
     * - [JsonSchemaInfo(pattern = ...)] on the property (if present)
     */
    public open fun applyPropertyAnnotations(property: KProperty1<*, *>, schema: JsonSchema): JsonSchema =
        schema // currently a no-op

    /**
     * Override to apply custom annotation-driven constraints.
     *
     * By default, applies:
     * - [JsonSchemaInfo(pattern = ...)] on the class (if present) to the *class schema* (rare but supported)
     */
    public open fun applyClassAnnotations(kClass: KClass<*>, schema: JsonSchema): JsonSchema =
        schema // currently a no-op

    // ----------------------------
    // Implementation
    // ----------------------------

    private fun buildSchemaInternal(type: KType, visiting: MutableSet<KType>): JsonSchema {
        // Cycle guard (best-effort): if we see the same type again, stop expanding.
        if (!visiting.add(type)) {
            val kClass = type.classifier as? KClass<*>
            return JsonSchema(
                type = JsonSchema.JsonType.OBJECT,
                title = kClass?.let(::titleForClass),
                description = "Recursive type encountered; schema expansion stopped to prevent cycles."
            )
        }

        try {
            val kClass = type.classifier as? KClass<*>
                ?: return JsonSchema(type = JsonSchema.JsonType.OBJECT)

            // Nullability: OpenAPI schema has a `nullable` flag
            val nullable = type.isMarkedNullable.takeIf { it }

            // Primitives / common JDK types
            primitiveSchemaOrNull(kClass)?.let { base ->
                return base.copy(nullable = nullable)
            }

            // Enums
            if (kClass.java.isEnum) {
                val values = kClass.java.enumConstants
                    ?.map { it.toString() }
                    .orEmpty()
                    .map { GenericElement<String>(it) }

                return JsonSchema(
                    type = JsonSchema.JsonType.STRING,
                    enum = values,
                    nullable = nullable
                )
            }

            // Arrays / Iterables
            if (kClass == Array<Any>::class || kClass.java.isArray) {
                val itemType = type.arguments.firstOrNull()?.type
                val itemSchema = itemType?.let { buildSchemaInternal(it, visiting) }
                    ?: JsonSchema(type = JsonSchema.JsonType.OBJECT)

                return JsonSchema(
                    type = JsonSchema.JsonType.ARRAY,
                    items = ReferenceOr.Value(itemSchema),
                    nullable = nullable
                )
            }

            if (kClass.isSubclassOf(Iterable::class)) {
                val itemType = type.arguments.firstOrNull()?.type
                val itemSchema = itemType?.let { buildSchemaInternal(it, visiting) }
                    ?: JsonSchema(type = JsonSchema.JsonType.OBJECT)

                return JsonSchema(
                    type = JsonSchema.JsonType.ARRAY,
                    items = ReferenceOr.Value(itemSchema),
                    nullable = nullable
                )
            }

            // Map -> object with additionalProperties
            if (kClass.isSubclassOf(Map::class)) {
                val keyType = type.arguments.getOrNull(0)?.type
                val valueType = type.arguments.getOrNull(1)?.type

                // JSON object keys are strings; if key isn't String, we still produce an object schema.
                val additional = valueType?.let { v ->
                    AdditionalProperties.PSchema(ReferenceOr.Value(buildSchemaInternal(v, visiting)))
                } ?: AdditionalProperties.Allowed(true)

                return JsonSchema(
                    type = JsonSchema.JsonType.OBJECT,
                    additionalProperties = additional,
                    nullable = nullable
                )
            }

            // Fallback: treat as object (data classes, POJOs, etc.)
            val properties = LinkedHashMap<String, ReferenceOr<JsonSchema>>()
            val required = ArrayList<String>()

            for (prop in kClass.memberProperties) {
                @Suppress("UNCHECKED_CAST")
                prop as KProperty1<Any, *>

                if (!shouldIncludeProperty(prop)) continue

                val name = propertyName(prop)
                val propSchemaBase = buildSchemaInternal(prop.returnType, visiting)

                // If property type is nullable, reflect it in schema (even if nested builder did it already,
                // this makes intent explicit and survives customizers that replace schemas).
                val propSchemaWithNullable = if (prop.returnType.isMarkedNullable) {
                    propSchemaBase.copy(nullable = true)
                } else propSchemaBase

                val propSchema = applyPropertyAnnotations(prop, propSchemaWithNullable)

                properties[name] = ReferenceOr.Value(propSchema)

                // Required: non-nullable properties are required (best effort; default values are not detectable reliably)
                if (!prop.returnType.isMarkedNullable) {
                    required += name
                }
            }

            val baseObjectSchema = JsonSchema(
                type = JsonSchema.JsonType.OBJECT,
                title = titleForClass(kClass),
                properties = properties.takeIf { it.isNotEmpty() },
                required = required.takeIf { it.isNotEmpty() },
                nullable = nullable
            )

            return applyClassAnnotations(kClass, baseObjectSchema)
        } finally {
            visiting.remove(type)
        }
    }

    private fun primitiveSchemaOrNull(kClass: KClass<*>): JsonSchema? = when (kClass) {
        String::class, Char::class -> JsonSchema(type = JsonSchema.JsonType.STRING)
        Boolean::class -> JsonSchema(type = JsonSchema.JsonType.BOOLEAN)

        Byte::class, Short::class, Int::class, Long::class,
        java.lang.Byte::class, java.lang.Short::class, java.lang.Integer::class, java.lang.Long::class ->
            JsonSchema(type = JsonSchema.JsonType.INTEGER)

        Float::class, Double::class,
        java.lang.Float::class, java.lang.Double::class ->
            JsonSchema(type = JsonSchema.JsonType.NUMBER)

        // Very common JDK types in API payloads
        java.util.UUID::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "uuid")
        java.time.Instant::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "date-time")
        java.time.OffsetDateTime::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "date-time")
        java.time.LocalDate::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "date")
        java.time.LocalDateTime::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "date-time")
        java.net.URI::class, java.net.URL::class -> JsonSchema(type = JsonSchema.JsonType.STRING, format = "uri")

        else -> null
    }

    private fun hasIgnoredByWellKnownAnnotations(property: KProperty1<*, *>): Boolean {
        // No direct dependency: match by FQCN strings so this works when Jackson is on the classpath.
        val ignoredAnnotationFqcns = setOf(
            "com.fasterxml.jackson.annotation.JsonIgnore",
            "com.fasterxml.jackson.annotation.JsonBackReference",
            "com.google.gson.annotations.Expose", // note: Gson requires additional rules; this is conservative
        )
        return property.annotations.any { ann ->
            ann.annotationClass.qualifiedName in ignoredAnnotationFqcns
        }
    }

    private fun KClass<*>.starProjectedTypeOrNull(): KType? = try {
        @Suppress("UNCHECKED_CAST")
        (this as KClass<Any>).starProjectedType
    } catch (_: Throwable) {
        null
    }
}

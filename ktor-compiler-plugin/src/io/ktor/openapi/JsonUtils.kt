package io.ktor.openapi

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

@Serializable
enum class JsonPrimitive {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
}

fun findJsonPrimitiveType(name: String): JsonPrimitive? {
    val classId = ClassId(
        StandardClassIds.BASE_KOTLIN_PACKAGE,
        Name.identifier(name)
    )
    return classId.toJsonType()
}

fun ClassId.toJsonType(): JsonPrimitive? =
    when(this) {
        // Integer types
        StandardClassIds.Int,
        StandardClassIds.UInt,
        StandardClassIds.Short,
        StandardClassIds.UShort,
        StandardClassIds.Byte,
        StandardClassIds.UByte,
        StandardClassIds.Long,
        StandardClassIds.ULong ->
            JsonPrimitive.INTEGER

        // Number types
        StandardClassIds.Float,
        StandardClassIds.Double,
        StandardClassIds.Number ->
            JsonPrimitive.NUMBER

        // Boolean type
        StandardClassIds.Boolean ->
            JsonPrimitive.BOOLEAN

        // String types
        StandardClassIds.String,
        StandardClassIds.Char ->
            JsonPrimitive.STRING

        // For all other classes, treat as custom object schema
        else -> null
    }
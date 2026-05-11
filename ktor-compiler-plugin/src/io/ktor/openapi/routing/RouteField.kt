package io.ktor.openapi.routing

import io.ktor.openapi.model.ExtensionAttribute
import io.ktor.openapi.model.ModelAttribute
import io.ktor.openapi.model.SchemaAttribute
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface RouteField {

    fun merge(other: RouteField): RouteField? =
        if (this::class == other::class) this else null

    sealed interface SchemaHolder : RouteField {
        val typeReference: TypeReference?
        val attributes: Map<ModelAttribute, String>

        @Suppress("UNCHECKED_CAST")
        val extensionAttributes: Map<ExtensionAttribute, String> get() =
            attributes.filterKeys { it is ExtensionAttribute } as Map<ExtensionAttribute, String>

        @Suppress("UNCHECKED_CAST")
        val schemaAttributes: Map<SchemaAttribute, String> get() =
            attributes.filterKeys { it is SchemaAttribute } as Map<SchemaAttribute, String>
    }

    sealed interface Content : SchemaHolder {
        val contentType: LocalReference?
        val description: String?
    }

    sealed interface ParameterOrHeader : SchemaHolder {
        val name: LocalReference
        val description: String?
    }

    /**
     * Query parameter, path variable, header, or cookie.
     */
    data class Parameter(
        val `in`: ParamIn? = null,
        override val name: LocalReference,
        override val typeReference: TypeReference? = null,
        override val description: String? = null,
        override val attributes: Map<ModelAttribute, String> = emptyMap(),
    ) : ParameterOrHeader {
        override fun merge(other: RouteField): RouteField? =
            if (other is Parameter && name.key == other.name.key) copy(
                `in` = `in` ?: other.`in`,
                typeReference = typeReference ?: other.typeReference,
                description = description ?: other.description,
                attributes = other.attributes + attributes,
            ) else null
    }

    object Ignore: RouteField

    data class OperationId(val value: String) : RouteField {
        override fun merge(other: RouteField): RouteField? =
            if (other is OperationId && value == other.value) this else null
    }

    /**
     * Associates the endpoint with a tag for grouping.
     *
     * Format: `@tag TagName`
     */
    data class Tag(val name: String) : RouteField {
        override fun merge(other: RouteField): RouteField? =
            if (other is Tag && name == other.name) this else null
    }

    /**
     * Documents the request body type.
     *
     * Format: `@body [Type] description`
     */
    data class Body(
        override val contentType: LocalReference? = null,
        override val typeReference: TypeReference? = null,
        override val description: String? = null,
        override val attributes: Map<ModelAttribute, String> = emptyMap(),
    ) : Content {
        override fun merge(other: RouteField): RouteField? =
            if (other is Body) copy(
                contentType = contentType ?: other.contentType,
                typeReference = typeReference ?: other.typeReference,
                description = description ?: other.description,
                attributes = other.attributes + attributes,
            ) else null
    }

    data class ResponseHeader(
        override val name: LocalReference,
        override val typeReference: TypeReference? = null,
        override val description: String? = null,
        override val attributes: Map<ModelAttribute, String> = emptyMap(),
    ) : ParameterOrHeader

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    data class Response(
        val code: LocalReference? = null,
        override val contentType: LocalReference? = null,
        override val typeReference: TypeReference? = null,
        override val description: String? = null,
        override val attributes: Map<ModelAttribute, String> = emptyMap(),
    ) : Content {
        override fun merge(other: RouteField): RouteField? =
            if (other is Response && code?.key == other.code?.key)
                copy(
                    contentType = contentType ?: other.contentType,
                    typeReference = typeReference ?: other.typeReference,
                    description = description ?: other.description,
                    attributes = other.attributes + attributes,
                )
            else null
    }

    /**
     * Marks an endpoint as deprecated.
     *
     * Format: `@deprecated reason`
     */
    data class Deprecated(val reason: String) : RouteField

    /**
     * Provides a detailed endpoint description.
     *
     * Format: `@description text`
     */
    data class Description(val text: String) : RouteField

    /**
     * Provides a link to external documentation.
     *
     * Format: `@externalDocs url description`
     */
    data class ExternalDocs(val url: String, val description: String?): RouteField

    /**
     * Provides a summary of the endpoint.
     * Format: `@summary text`
     */
    data class Summary(val text: String) : RouteField

    /**
     * Documents security requirements.
     *
     * Special cases include "*" when any scheme can be used, or null when security is optional.
     *
     * Format: `@security scheme`
     */
    data class Security(
        val scheme: String?,
        val scopes: List<String>? = null,
    ) : RouteField {
        companion object {
            val All = Security("*")
            val Optional = Security(null)
        }

        override fun merge(other: RouteField): RouteField? =
            if (other is Security && scheme == other.scheme) this else null
    }
}

typealias RouteFieldList = List<RouteField>

/**
 * Merges two lists of route fields into a new list.
 */
fun RouteFieldList.merge(other: RouteFieldList) = buildList {
    val otherMutable = other.toMutableList()
    for (field in this@merge) {
        val mergedField = otherMutable.indices.firstNotNullOfOrNull { i ->
            field.merge(otherMutable[i])?.also {
                otherMutable.removeAt(i)
            }
        }
        add(mergedField ?: field)
    }
    // include unmatched fields from the other list
    addAll(otherMutable)
}

enum class ParamIn {
    PATH,
    QUERY,
    HEADER,
    COOKIE,
}
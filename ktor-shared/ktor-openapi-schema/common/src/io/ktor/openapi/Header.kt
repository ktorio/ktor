package io.ktor.openapi

import kotlinx.serialization.Serializable

/**
 * Header fields have the same meaning as for 'Param'. Style is always treated as [Style.simple], as
 * it is the only value allowed for headers.
 */
@Serializable
public data class Header(
    /** A short description of the header. */
    public val description: String? = null,
    public val required: Boolean? = null,
    public val deprecated: Boolean? = null,
    public val allowEmptyValue: Boolean? = null,
    public val explode: Boolean? = null,
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    public val schema: ReferenceOr<Schema>? = null,
)

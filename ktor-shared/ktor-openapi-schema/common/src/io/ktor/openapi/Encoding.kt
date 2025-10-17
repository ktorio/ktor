/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.openapi

import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A single encoding definition applied to a single schema property. */
@Serializable(Encoding.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Encoding(
    /**
     * The Content-Type for encoding a specific property. Default value depends on the property type:
     * - for string with format being binary – application/octet-stream;
     * - for other primitive types – text/plain
     * - for object - application/json
     * - for array – the default is defined based on the inner type. The value can be a specific media
     *   type (e.g. application/json), a wildcard media type (e.g. image&#47;&#42;), or a
     *   comma-separated list of the two types.
     */
    @Serializable(ContentTypeSerializer::class)
    public val contentType: ContentType,
    /**
     * A map allowing additional information to be provided as headers, for example
     * Content-Disposition. Content-Type is described separately and SHALL be ignored in this section.
     * This property SHALL be ignored if the request body media type is not a multipart
     */
    public val headers: Map<String, ReferenceOr<Header>>? = null,
    /**
     * Describes how a specific property value will be serialized depending on its type. See [Style]
     * for details on the style property. The behavior follows the same values as query parameters,
     * including default values. This property SHALL be ignored if the request body media type is not
     * application/x-www-form-urlencoded.
     */
    public val style: String? = null,
    /**
     * When this is true, property values of type array or object generate separate parameters for
     * each value of the array, or key-value-pair of the map. For other types of properties this
     * property has no effect. When style is form, the default value is true. For all other styles,
     * the default value is false. This property SHALL be ignored if the request body media type is
     * not application/x-www-form-urlencoded.
     */
    public val explode: Boolean? = null,
    /**
     * Determines whether the parameter value SHOULD allow reserved characters, as defined by RFC3986
     * :/?#[]@!$&'()*+,;= to be included without percent-encoding. The default value is false. This
     * property SHALL be ignored if the request body media type is not
     * application/x-www-form-urlencoded.
     */
    public val allowReserved: Boolean? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Encoding>(
            generatedSerializer(),
            { enc, extensions -> enc.copy(extensions = extensions) }
        )
    }
}

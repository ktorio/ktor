/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

@Serializable(Xml.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Xml(
    /**
     * Replaces the name of the element/attribute used for the described schema property. When defined
     * within the @'OpenApiItems'@ (items), it will affect the name of the individual XML elements
     * within the list. When defined alongside type being array (outside the items), it will affect
     * the wrapping element and only if wrapped is true. If wrapped is false, it will be ignored.
     */
    public val name: String? = null,
    /** The URL of the namespace definition. Value SHOULD be in the form of a URL. */
    public val namespace: String? = null,
    /** The prefix to be used for the name. */
    public val prefix: String? = null,
    /**
     * Declares whether the property definition translates to an attribute instead of an element.
     * Default value is @False@.
     */
    public val attribute: Boolean? = null,
    /**
     * MAY be used only for an array definition. Signifies whether the array is wrapped (for
     * example, @\<books\>\<book/\>\<book/\>\</books\>@) or unwrapped (@\<book/\>\<book/\>@). Default
     * value is
     *
     * @False@. The definition takes effect only when defined alongside type being array (outside the
     *   items).
     */
    public val wrapped: Boolean? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data.
     */
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Xml>(
            generatedSerializer(),
            { xml, extensions -> xml.copy(extensions = extensions) }
        )
    }
}

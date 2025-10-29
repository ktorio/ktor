/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.Serializable

/** Allows referencing an external resource for extended documentation. */
@Serializable
public data class ExternalDocs(
    /**
     * A short description of the target documentation. CommonMark syntax MAY be used for rich text
     * representation.
     */
    public val description: String? = null,
    /** The URL for the target documentation. Value MUST be in the format of a URL. */
    public val url: String,
)

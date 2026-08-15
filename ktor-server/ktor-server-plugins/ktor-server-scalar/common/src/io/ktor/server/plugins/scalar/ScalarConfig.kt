/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.scalar

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*

/**
 * A configuration for the Scalar UI endpoint.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig)
 */
@KtorDsl
public class ScalarConfig private constructor(
    private val docBuilder: OpenApiDoc.Builder
) : OpenApiDocDsl by docBuilder {
    public constructor() : this(OpenApiDoc.Builder())

    internal var customStyle: String? = null

    /**
     * Defines the source of the OpenAPI specification.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.source)
     */
    public var source: OpenApiDocSource = OpenApiDocSource.FirstOf(
        OpenApiDocSource.File("openapi/documentation.yaml"),
        OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml),
    )

    /**
     * Relative path from the scalar URL root to the specification file.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.remotePath)
     */
    public var remotePath: String = "documentation.yaml"

    /**
     * Specifies a CDN URL for the Scalar API Reference standalone script.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.cdnUrl)
     */
    public var cdnUrl: String = "https://cdn.jsdelivr.net/npm/@scalar/api-reference"

    /**
     * Specifies the theme for Scalar UI (e.g. "purple", "moon", "solarized", "saturn", "kepler", "mars", "deepSpace", "none").
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.theme)
     */
    public var theme: String = "purple"

    /**
     * Specifies the layout style for Scalar UI (e.g. "modern", "classic").
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.layout)
     */
    public var layout: String = "modern"

    /**
     * Whether to show the sidebar in Scalar UI.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.showSidebar)
     */
    public var showSidebar: Boolean = true

    /**
     * Specifies a URL for a custom CSS applied to Scalar UI.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.customStyle)
     */
    public fun customStyle(path: String?) {
        customStyle = path
    }

    /**
     * Location of the Scalar favicon.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.ScalarConfig.faviconLocation)
     */
    public var faviconLocation: String? = null

    /**
     * Base document built for route-based OpenAPI generation.
     */
    internal fun buildBaseDoc(): OpenApiDoc = docBuilder.build()
}

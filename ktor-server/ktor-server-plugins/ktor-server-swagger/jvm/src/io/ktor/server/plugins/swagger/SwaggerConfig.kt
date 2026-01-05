/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.annotate.OpenApiDocSource
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiDocDsl

/**
 * A configuration for the Swagger UI endpoint.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig)
 */
public class SwaggerConfig private constructor(
    private val docBuilder: OpenApiDoc.Builder
) : OpenApiDocDsl by docBuilder {
    public constructor() : this(OpenApiDoc.Builder())

    internal var customStyle: String? = null

    /**
     * Defines the source of the OpenAPI specification.
     */
    public var source: OpenApiDocSource = OpenApiDocSource.FirstOf(
        OpenApiDocSource.FileSource("openapi/documentation.yaml"),
        OpenApiDocSource.RoutingSource(contentType = ContentType.Application.Yaml),
    )

    /**
     * Relative path from the swagger URL root to the specification file.
     */
    public var remotePath: String = "documentation.yaml"

    /**
     * Specifies a Swagger UI version to use.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.version)
     */
    public var version: String = "5.17.12"

    /**
     * Specifies a URL for a custom CSS applied to a Swagger UI.
     *
     * Example: https://unpkg.com/swagger-ui-themes@3.0.1/themes/3.x/theme-monokai.css
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.customStyle)
     */
    public fun customStyle(path: String?) {
        customStyle = path
    }

    /**
     * Swagger package location
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.packageLocation)
     */
    public var packageLocation: String = "https://unpkg.com/swagger-ui-dist"

    /**
     * Whether to allow [deep linking in Swagger UI](https://swagger.io/docs/open-source-tools/swagger-ui/usage/deep-linking/).
     *
     * Defaults to `false`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.deepLinking)
     */
    public var deepLinking: Boolean = false

    /**
     * Swagger favicon location
     */
    public var faviconLocation: String = "https://unpkg.com/swagger-ui-dist@$version/favicon-32x32.png"

    /**
     * Base document is built for route-based OpenAPI generation.
     */
    internal fun buildBaseDoc(): OpenApiDoc =
        docBuilder.build()
}

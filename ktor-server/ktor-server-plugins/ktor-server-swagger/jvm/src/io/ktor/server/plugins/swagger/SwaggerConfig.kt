/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.routing.openapi.*

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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.source)
     */
    public var source: OpenApiDocSource = OpenApiDocSource.FirstOf(
        OpenApiDocSource.File("openapi/documentation.yaml"),
        OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml),
    )

    /**
     * Relative path from the swagger URL root to the specification file.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.remotePath)
     */
    public var remotePath: String = "documentation.yaml"

    /**
     * Specifies a Swagger UI version to use.
     *
     * Defaults to `5.31.0`. OpenAPI 3.1.x specifications require Swagger UI 5.0.0 or later; versions below 5.x only
     * support OpenAPI 3.0.x.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.version)
     */
    public var version: String = "5.31.0"

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
     * OAuth2 redirect URL passed to Swagger UI as `oauth2RedirectUrl`.
     *
     * When `null` (default), Swagger UI uses `window.location.origin + "{swaggerPath}/oauth2-redirect.html"`.
     *
     * Set an absolute URL when running behind a reverse proxy or using a custom redirect path.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.oauth2RedirectUrl)
     */
    public var oauth2RedirectUrl: String? = null

    /**
     * Swagger favicon location
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.SwaggerConfig.faviconLocation)
     */
    public var faviconLocation: String = "https://unpkg.com/swagger-ui-dist@$version/favicon-32x32.png"

    /**
     * Base document is built for route-based OpenAPI generation.
     */
    internal fun buildBaseDoc(): OpenApiDoc =
        docBuilder.build()
}

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.swagger.codegen.v3.*
import io.swagger.codegen.v3.generators.html.*
import io.swagger.parser.*
import io.swagger.v3.parser.core.models.*

/**
 * Configuration for OpenAPI endpoint.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig)
 */
public class OpenAPIConfig {
    /**
     * Specifies a parser used to parse OpenAPI.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.parser)
     */
    public var parser: OpenAPIParser = OpenAPIParser()

    /**
     * Specifies options of the OpenAPI generator.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.opts)
     */
    public var opts: ClientOptInput = ClientOptInput()

    /**
     * Specifies a generator used to generate OpenAPI.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.generator)
     */
    public var generator: Generator = DefaultGenerator()

    /**
     * Specifies a code generator for [OpenAPIConfig].
     *
     * See also [StaticHtml2Codegen], [StaticHtmlCodegen] and etc.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.codegen)
     */
    public var codegen: CodegenConfig = StaticHtml2Codegen()

    /**
     * Provides access to options of the OpenAPI format parser.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.options)
     */
    public var options: ParseOptions = ParseOptions()
}

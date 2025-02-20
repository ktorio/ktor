/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import java.net.*
import kotlin.random.*

/**
 * Web resources serve configuration
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig)
 */
public class WebResourcesConfig internal constructor() {
    /**
     * Path predicates to be included. All files will be served if no include rules specified.
     * A path provided to a predicate is always slash-separated (`/`).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig.includes)
     */
    public val includes: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Path predicates to be excluded. By default WEB-INF directory is excluded.
     * A path provided to a predicate is always slash-separated (`/`).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig.excludes)
     */
    public val excludes: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Content-type resolution, uses [defaultForFileExtension] by default
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig.mimeResolve)
     */
    public var mimeResolve: (URL) -> ContentType = { ContentType.defaultForFilePath(it.path) }

    /**
     * Add [predicate] to [includes]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig.include)
     *
     * @see includes
     */
    public fun include(predicate: (path: String) -> Boolean) {
        includes.add(predicate)
    }

    /**
     * Add [predicate] exclusion rule to [excludes]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.WebResourcesConfig.exclude)
     *
     * @see excludes
     */
    public fun exclude(predicate: (path: String) -> Boolean) {
        excludes.add(predicate)
    }

    init {
        excludes.add { path -> path == "WEB-INF" || path.startsWith("WEB-INF/") }
    }
}

/**
 * Serve web resources (usually a directory named webapp containing WEB-INF/web.xml). Note that WEB-INF directory
 * itself is not served by default.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.webResources)
 *
 * @param subPath slash-delimited web resources root path (relative to webapp directory)
 */
@OptIn(InternalAPI::class)
public fun Route.webResources(subPath: String = "/", configure: WebResourcesConfig.() -> Unit = {}) {
    val config = WebResourcesConfig().apply(configure)
    val pathParameterName = pathParameterName + "_" + Random.nextInt(0, Int.MAX_VALUE)
    val prefix = subPath.split('/', '\\').filter { it.isNotEmpty() }

    get("{$pathParameterName...}") {
        val filteredPath = call.parameters.getAll(pathParameterName)?.normalizePathComponents() ?: return@get
        val path = (prefix + filteredPath).joinToString("/", prefix = "/")

        if (config.excludes.any { it(path) }) {
            return@get
        }
        if (config.includes.isNotEmpty() && config.includes.none { it(path) }) {
            return@get
        }

        val url = call.application.attributes.getOrNull(ServletContextAttribute)?.getResource(path) ?: return@get
        val content = resourceClasspathResource(url, path, config.mimeResolve) ?: return@get
        call.respond(content)
    }
}

private const val pathParameterName = "web_resources_path_parameter"

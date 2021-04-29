/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlin.random.*

/**
 * Web resources serve configuration
 */
public class WebResourcesConfig
@Deprecated(
    "Direct instantiation will be impossible in 2.0.0. " +
        "Use Route.webResources {} function instead " +
        "or file an issue describing why do you need it."
)
constructor() {
    /**
     * Path predicates to be included. All files will be served if no include rules specified.
     * A path provided to a predicate is always slash-separated (`/`).
     */
    public val includes: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Path predicates to be excluded. By default WEB-INF directory is excluded.
     * A path provided to a predicate is always slash-separated (`/`).
     */
    public val excludes: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Content-type resolution, uses [defaultForFileExtension] by default
     */
    public var mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }

    /**
     * Add [predicate] to [includes]
     * @see includes
     */
    public fun include(predicate: (path: String) -> Boolean) {
        includes.add(predicate)
    }

    /**
     * Add [predicate] exclusion rule to [excludes]
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
 * @param subPath slash-delimited web resources root path (relative to webapp directory)
 */
public fun Route.webResources(subPath: String = "/", configure: WebResourcesConfig.() -> Unit = {}) {
    @Suppress("DEPRECATION")
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

        val url = application.attributes.getOrNull(ServletContextAttribute)?.getResource(path) ?: return@get
        val content = resourceClasspathResource(url, path, config.mimeResolve) ?: return@get
        call.respond(content)
    }
}

private const val pathParameterName = "web_resources_path_parameter"

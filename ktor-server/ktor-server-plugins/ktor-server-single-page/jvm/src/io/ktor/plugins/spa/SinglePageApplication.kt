/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.plugins.spa

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.io.*

/**
 * A plugin that allows you to serve a single-page application
 *
 * A basic plugin configuration for the application served from the filesPath folder
 * with index.html as a default file:
 *
 * install(SinglePageApplication) {
 *   filesPath = "application/project_path"
 * }
 */
public val SinglePageApplication: ApplicationPlugin<Application, SpaConfiguration, PluginInstance> = createApplicationPlugin(
    "SinglePage",
    { SpaConfiguration() }
) {
    val defaultPage: String = pluginConfig.defaultPage
    val applicationRoute: String = pluginConfig.applicationRoute
    val filesPath: String = pluginConfig.filesPath
    val ignoredFiles: MutableList<(String) -> Boolean> = pluginConfig.ignoredFiles
    val usePackageNames: Boolean = pluginConfig.useResources

    fun isUriStartWith(uri: String) =
        uri.startsWith(applicationRoute) || uri.startsWith("/$applicationRoute")

    application.routing {
        static(applicationRoute) {
            if (usePackageNames) {
                resources(filesPath)
                defaultResource(defaultPage, filesPath)
            } else {
                staticRootFolder = File(filesPath)
                files(".")
                default(defaultPage)
            }
        }
    }

    onCall { call ->
        val requestUrl = call.request.uri

        if (!isUriStartWith(requestUrl)) return@onCall

        if (ignoredFiles.firstOrNull { it.invoke(requestUrl) } != null) {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}

/**
 * Configuration for the [SinglePageApplication] plugin
 */
public class SpaConfiguration(
    /**
     * The default name of a file or resource to serve when [applicationRoute] is requested
     */
    public var defaultPage: String = "index.html",

    /**
     * The URL path under which the content should be served
     */
    public var applicationRoute: String = "/",

    /**
     * The path under which the static content is located.
     * Corresponds to the folder path if the [useResources] is false, resource path otherwise
     */
    public var filesPath: String = "",

    /**
     * Specifies if static content is a resource package with true or folder with false
     */
    public var useResources: Boolean = false,

    /**
     * A list of callbacks checking if a file or resource in [filesPath] is ignored.
     * Requests for such files or resources fails with the 403 Forbidden status
     */
    internal val ignoredFiles: MutableList<(String) -> Boolean> = mutableListOf()
)

/**
 * Registers a [block] in [ignoredFiles]
 * [block] returns true if [path] should be ignored.
 */
public fun SpaConfiguration.ignoreFiles(block: (path: String) -> Boolean) {
    ignoredFiles += block
}

/**
 * Creates an application configuration for the Angular project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SpaConfiguration.angular(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the React project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SpaConfiguration.react(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Vue project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SpaConfiguration.vue(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Ember project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SpaConfiguration.ember(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Backbone project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SpaConfiguration.backbone(filesPath: String) {
    this.filesPath = filesPath
}

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.plugins

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
 *
 */
public class SinglePage internal constructor(configuration: Configuration) {

    internal val defaultPage: String = configuration.defaultPage

    internal val applicationRoute: String = configuration.applicationRoute

    internal val filesPath: String = configuration.filesPath

    internal val ignoredRoutes: List<Regex> = configuration.ignoredRoutes

    internal val usePackageNames: Boolean = configuration.usePackageNames

    public class Configuration(
        /**
         *
         */
        public var defaultPage: String = "index.html",

        /**
         *
         */
        public var applicationRoute: String = "/",

        /**
         *
         */
        public var filesPath: String = "",

        /**
         *
         */
        public var usePackageNames: Boolean = false,

        /**
         *
         */
        public var ignoredRoutes: List<Regex> = listOf()
    )

    /**
     *
     */
    public companion object Plugin : ApplicationPlugin<Application, Configuration, SinglePage> {
        override val key: AttributeKey<SinglePage> = AttributeKey("SinglePage")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): SinglePage {
            val plugin = SinglePage(Configuration().apply(configure))

            pipeline.routing {
                static(plugin.applicationRoute) {
                    if (plugin.usePackageNames) {
                        resources(plugin.filesPath)
                        defaultResource(plugin.defaultPage, plugin.filesPath)
                    } else {
                        staticRootFolder = File(plugin.filesPath)
                        files(".")
                        default(plugin.defaultPage)
                    }
                }
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
                plugin.interceptCall(this)
            }
            return plugin
        }
    }

    private suspend fun interceptCall(
        context: PipelineContext<Any, ApplicationCall>,
    ) = with(context) context@{

        val call = context.call
        val requestUrl = call.request.uri

        if (call.attributes.contains(key)) return@context
        if (!isUriStartWith(requestUrl)) return@context

        call.attributes.put(key, this@SinglePage)

        if (ignoredRoutes.firstOrNull { requestUrl.contains(it) } != null) {
            call.response.status(HttpStatusCode.NotFound)
            call.respondText("File with path $requestUrl is in ignore list")
            finish()
        }
    }

    private fun isUriStartWith(uri: String) =
        uri.startsWith(applicationRoute) || uri.startsWith("/${applicationRoute}")
}

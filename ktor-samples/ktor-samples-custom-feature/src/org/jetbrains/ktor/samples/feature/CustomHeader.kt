package org.jetbrains.ktor.samples.feature

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class CustomHeader(configuration: Configuration) {
    // get an immutable snapshot of a configuration values
    val name = configuration.headerName
    val value = configuration.headerValue

    // Feature configuration class
    class Configuration {
        // mutable properties with default values so user can modify it
        var headerName = "Custom"
        var headerValue = "Value"
    }

    // Body of the feature
    private suspend fun intercept(context: PipelineContext<Unit>) {
        // Add custom header to the response
        context.call.response.header(name, value)
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, CustomHeader.Configuration, CustomHeader> {
        override val key = AttributeKey<CustomHeader>("CustomFeature")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): CustomHeader {
            // Call user code to configure a feature
            val configuration = Configuration().apply(configure)

            // Create a feature instance
            val feature = CustomHeader(configuration)

            // Install an interceptor that will be run on each call and call feature instance
            pipeline.intercept(ApplicationCallPipeline.Call) {
                feature.intercept(this)
            }

            // Return a feature instance so that client code can use it
            return feature
        }
    }
}
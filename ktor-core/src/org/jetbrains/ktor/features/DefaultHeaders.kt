package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

class DefaultHeaders(config: Configuration) {
    val headers = config.headers.build()
    private var cachedDateTimeStamp: Long = 0L
    @Volatile private var cachedDateText: String = ZonedDateTime.now(ZoneOffset.UTC).toHttpDateString()

    class Configuration {
        val headers = ValuesMapBuilder()
        fun header(name: String, value: String) = headers.append(name, value)
    }

    private fun intercept(call: ApplicationCall) {
        appendDateHeader(call)
        headers.entries().forEach { entry -> entry.value.forEach { call.response.header(entry.key, it) } }
    }

    private fun appendDateHeader(call: ApplicationCall) {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = System.currentTimeMillis()
        if (captureCached + 1000 < currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            cachedDateText = now.toHttpDateString()
        }
        call.response.header("Date", cachedDateText)
    }

    companion object Feature : ApplicationFeature<Application, Configuration, DefaultHeaders> {
        override val key = AttributeKey<DefaultHeaders>("Default Headers")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DefaultHeaders {
            val config = Configuration().apply(configure)
            if (config.headers.getAll(HttpHeaders.Server) == null) {
                val applicationClass = pipeline.javaClass

                val ktorPackageName: String = Application::class.java.`package`.implementationTitle ?: "ktor"
                val ktorPackageVersion: String = Application::class.java.`package`.implementationVersion ?: "debug"
                val applicationPackageName: String = applicationClass.`package`.implementationTitle ?: applicationClass.simpleName
                val applicationPackageVersion: String = applicationClass.`package`.implementationVersion ?: "debug"

                config.headers.append(HttpHeaders.Server, "$applicationPackageName/$applicationPackageVersion $ktorPackageName/$ktorPackageVersion")
            }

            val feature = DefaultHeaders(config)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(it) }
            return feature
        }
    }
}
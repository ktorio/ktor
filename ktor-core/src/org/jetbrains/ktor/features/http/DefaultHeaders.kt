package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

class DefaultHeaders {
    private val headers = ValuesMapBuilder()
    private var cachedDateTimeStamp: Long = 0L
    @Volatile private var cachedDateText: String = ZonedDateTime.now(ZoneOffset.UTC).toHttpDateString()

    fun header(name: String, value: String) = headers.append(name, value)

    fun appendHeadersTo(call: ApplicationCall) {
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

    companion object Feature : ApplicationFeature<Application, DefaultHeaders> {
        override val key = AttributeKey<DefaultHeaders>("Default Headers")

        override fun install(pipeline: Application, configure: DefaultHeaders.() -> Unit): DefaultHeaders {
            val feature = DefaultHeaders()
            feature.configure()

            if (feature.headers.getAll(HttpHeaders.Server) == null) {
                val applicationClass = pipeline.javaClass

                val ktorPackageName: String = Application::class.java.`package`.implementationTitle ?: "ktor"
                val ktorPackageVersion: String = Application::class.java.`package`.implementationVersion ?: "debug"

                val applicationPackageName: String = applicationClass.`package`.implementationTitle ?: applicationClass.simpleName
                val applicationPackageVersion: String = applicationClass.`package`.implementationVersion ?: "debug"


                feature.headers.append(HttpHeaders.Server, "$applicationPackageName/$applicationPackageVersion $ktorPackageName/$ktorPackageVersion")
            }

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call -> feature.appendHeadersTo(call) }
            return feature
        }
    }
}
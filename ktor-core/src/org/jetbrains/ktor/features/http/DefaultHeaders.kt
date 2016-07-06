package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

object DefaultHeaders : ApplicationFeature<Application, DefaultHeaders> {
    override val key = AttributeKey<DefaultHeaders>("Default Headers")

    private val headers = ValuesMapBuilder()

    override fun install(pipeline: Application, configure: DefaultHeaders.() -> Unit): DefaultHeaders {
        configure()

        if (headers.getAll(HttpHeaders.Server) == null) {
            val applicationClass = pipeline.javaClass

            val ktorPackageName: String = Application::class.java.`package`.implementationTitle ?: "ktor"
            val ktorPackageVersion: String = Application::class.java.`package`.implementationVersion ?: "debug"

            val applicationPackageName: String = applicationClass.`package`.implementationTitle ?: applicationClass.simpleName
            val applicationPackageVersion: String = applicationClass.`package`.implementationVersion ?: "debug"

            
            headers.append(HttpHeaders.Server, "$applicationPackageName/$applicationPackageVersion $ktorPackageName/$ktorPackageVersion")
        }

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.response.header("Date", LocalDateTime.now().toHttpDateString())
            headers.entries().forEach { entry -> entry.value.forEach { call.response.header(entry.key, it) } }
        }
        return this
    }

    fun header(name: String, value: String) = headers.append(name, value)
}
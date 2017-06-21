package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

class DefaultHeaders(config: Configuration) {
    val headers = config.headers.build()

    private val UTC = ZoneId.of("UTC")!! // it is very important to get it like that
    private val zoneUTCRules = UTC.rules

    private var cachedDateTimeStamp: Long = 0L
    @Volatile private var cachedDateText: String = ZonedDateTime.now(ZoneOffset.UTC).toHttpDateString()

    class Configuration {
        val headers = ValuesMapBuilder()
        fun header(name: String, value: String) = headers.append(name, value)
    }

    private fun intercept(call: ApplicationCall) {
        appendDateHeader(call)
        headers.forEach { name, value -> value.forEach { call.response.header(name, it) } }
    }

    // ZonedDateTime.now allocates too much so we reimplement it
    private fun now(): ZonedDateTime {
        // we shouldn't use ZoneOffset.UTC here otherwise we get to many allocations inside of Java Time implementation
        val instant = Clock.system(UTC).instant()
        val offset = zoneUTCRules.getOffset(instant)
        val ldt = LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset)

        return ZonedDateTime.ofInstant(ldt, offset, UTC)
    }

    private fun appendDateHeader(call: ApplicationCall) {
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = System.currentTimeMillis()
        if (captureCached + 1000 < currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            cachedDateText = now().toHttpDateString()
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
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
            return feature
        }
    }
}
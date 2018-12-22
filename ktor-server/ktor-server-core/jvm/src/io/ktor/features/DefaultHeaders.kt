package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import java.time.*

/**
 * Adds standard HTTP headers `Date` and `Server` and provides ability to specify other headers
 * that are included in responses.
 */
class DefaultHeaders(config: Configuration) {
    private val headers = config.headers.build()

    private val UTC = ZoneId.of("UTC")!! // it is very important to get it like that
    private val zoneUTCRules = UTC.rules

    private var cachedDateTimeStamp: Long = 0L
    @Volatile private var cachedDateText: String = ZonedDateTime.now(GreenwichMeanTime).toHttpDateString()

    /**
     * Configuration for [DefaultHeaders] feature.
     */
    class Configuration {
        /**
         * Provides a builder to append any custom headers to be sent with each request
         */
        internal val headers = HeadersBuilder()

        /**
         * Adds standard header property [name] with the specified [value].
         */
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

    /**
     * Installable feature for [DefaultHeaders].
     */
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
            pipeline.intercept(ApplicationCallPipeline.Features) { feature.intercept(call) }
            return feature
        }
    }
}
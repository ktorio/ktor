/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import org.slf4j.*
import org.slf4j.event.*

/**
 * A configuration for the [CallLogging] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig)
 */
@KtorDsl
public class CallLoggingConfig {
    internal var clock: () -> Long = { getTimeMillis() }
    internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
    internal val mdcEntries = mutableListOf<MDCEntry>()
    internal var formatCall: (ApplicationCall) -> String = ::defaultFormat
    internal var isColorsEnabled: Boolean = true
    internal var ignoreStaticContent: Boolean = false

    private val isAnsiSupported: Boolean = supportsAnsi()

    /**
     * Specifies a logging level for the [CallLogging] plugin.
     * The default level is [Level.INFO].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.level)
     */
    public var level: Level = Level.INFO

    /**
     * Specifies a [Logger] used to log requests.
     * By default, uses [ApplicationEnvironment.log].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.logger)
     */
    public var logger: Logger? = null

    /**
     * Allows you to add conditions for filtering requests.
     * In the example below, only requests made to `/api/v1` get into a log:
     * ```kotlin
     * filter { call ->
     *     call.request.path().startsWith("/api/v1")
     * }
     * ```
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.filter)
     *
     * @see [CallLogging]
     */
    public fun filter(predicate: (ApplicationCall) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Puts a diagnostic context value to [MDC] with the specified [name] and computed using the [provider] function.
     * A value is available in MDC only during [ApplicationCall] lifetime and is removed after a call processing.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.mdc)
     *
     * @see [CallLogging]
     */
    public fun mdc(name: String, provider: (ApplicationCall) -> String?) {
        mdcEntries.add(MDCEntry(name, provider))
    }

    /**
     * Allows you to configure a call log message.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.format)
     *
     * @see [CallLogging]
     */
    public fun format(formatter: (ApplicationCall) -> String) {
        formatCall = formatter
    }

    /**
     * Allows you to configure a clock that will be used to measure call processing time.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.clock)
     *
     * @see [CallLogging]
     */
    public fun clock(clock: () -> Long) {
        this.clock = clock
    }

    /**
     * Disables colors in a log message when a default formatter is used.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.disableDefaultColors)
     * */
    public fun disableDefaultColors() {
        isColorsEnabled = false
    }

    /**
     * Disables logging for static content files.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.calllogging.CallLoggingConfig.disableForStaticContent)
     * */
    public fun disableForStaticContent() {
        ignoreStaticContent = true
    }

    private fun defaultFormat(call: ApplicationCall): String =
        when (val status = call.response.status() ?: "Unhandled") {
            HttpStatusCode.Found -> "${colored(status as HttpStatusCode)}: " +
                "${call.request.toLogStringWithColors()} -> ${call.response.headers[HttpHeaders.Location]}"

            "Unhandled" -> "${colored(status, RED_COLOR)}: ${call.request.toLogStringWithColors()}"
            else -> "${colored(status as HttpStatusCode)}: ${call.request.toLogStringWithColors()}"
        }

    internal fun ApplicationRequest.toLogStringWithColors(): String =
        "${colored(httpMethod.value, CYAN_COLOR)} - ${path()} in ${call.processingTimeMillis(clock)}ms"

    private fun colored(status: HttpStatusCode): String {
        return when (status) {
            HttpStatusCode.Found, HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.Created -> colored(
                status,
                GREEN_COLOR
            )

            HttpStatusCode.Continue, HttpStatusCode.Processing, HttpStatusCode.PartialContent,
            HttpStatusCode.NotModified, HttpStatusCode.UseProxy, HttpStatusCode.UpgradeRequired,
            HttpStatusCode.NoContent -> colored(status, YELLOW_COLOR)

            else -> colored(status, RED_COLOR)
        }
    }

    private fun colored(value: Any, color: Color): String =
        if (isColorsEnabled && isAnsiSupported) {
            "\u001b[${color}m" + value.toString() + "\u001b[0m"
        } else {
            value.toString() // ignore color
        }
}

private typealias Color = Int

private const val RED_COLOR = 31
private const val GREEN_COLOR = 32
private const val YELLOW_COLOR = 33
private const val CYAN_COLOR = 36

private fun supportsAnsi(): Boolean {
    // TODO: Redirection to a file considers it a terminal
    if (System.getProperty("os.name").startsWith("Windows")) {
        val osVersion = System.getProperty("os.version")

        if (osVersion != null) {
            var major = 0
            for (c in osVersion) {
                if (c in '0'..'9') {
                    major = major * 10 + (c.code - '0'.code)
                } else {
                    break
                }
            }

            return major >= 10
        } else {
            return false
        }
    }

    if (System.getenv("COLORTERM") != null) {
        return true
    }

    val term = System.getenv("TERM")
    return term != null && (term.contains("color") || term.contains("xterm")
        || term.contains("screen") || term.contains("tmux"))
}

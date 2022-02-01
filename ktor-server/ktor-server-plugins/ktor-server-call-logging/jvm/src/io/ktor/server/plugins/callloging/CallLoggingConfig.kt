/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.callloging

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.fusesource.jansi.*
import org.slf4j.*
import org.slf4j.event.*

/**
 * Configuration for [CallLogging] plugin
 */
public class CallLoggingConfig {
    internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
    internal val mdcEntries = mutableListOf<MDCEntry>()
    internal var formatCall: (ApplicationCall) -> String = ::defaultFormat
    internal var isColorsEnabled: Boolean = true

    /**
     * Logging level for [CallLogging], default is [Level.INFO]
     */
    public var level: Level = Level.INFO

    /**
     * Customize [Logger], will default to [ApplicationEnvironment.log]
     */
    public var logger: Logger? = null

    /**
     * Log messages for calls matching a [predicate]
     */
    public fun filter(predicate: (ApplicationCall) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Put a diagnostic context value to [MDC] with the specified [name] and computed using [provider] function.
     * A value will be available in MDC only during [ApplicationCall] lifetime and will be removed after call
     * processing.
     */
    public fun mdc(name: String, provider: (ApplicationCall) -> String?) {
        mdcEntries.add(MDCEntry(name, provider))
    }

    /**
     * Configure application call log message.
     */
    public fun format(formatter: (ApplicationCall) -> String) {
        formatCall = formatter
    }

    /**
     * Disables colors in log message in case the default formatter was used.
     * */
    public fun disableDefaultColors() {
        isColorsEnabled = false
    }

    private fun defaultFormat(call: ApplicationCall): String =
        when (val status = call.response.status() ?: "Unhandled") {
            HttpStatusCode.Found -> "${colored(status as HttpStatusCode)}: " +
                "${call.request.toLogStringWithColors()} -> ${call.response.headers[HttpHeaders.Location]}"
            "Unhandled" -> "${colored(status, Ansi.Color.RED)}: ${call.request.toLogStringWithColors()}"
            else -> "${colored(status as HttpStatusCode)}: ${call.request.toLogStringWithColors()}"
        }

    internal fun ApplicationRequest.toLogStringWithColors(): String =
        "${colored(httpMethod.value, Ansi.Color.CYAN)} - ${path()}"

    private fun colored(status: HttpStatusCode): String {
        try {
            if (!AnsiConsole.isInstalled()) {
                AnsiConsole.systemInstall()
            }
        } catch (cause: Throwable) {
            isColorsEnabled = false // ignore colors if console was not installed
        }

        return when (status) {
            HttpStatusCode.Found, HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.Created -> colored(
                status,
                Ansi.Color.GREEN
            )
            HttpStatusCode.Continue, HttpStatusCode.Processing, HttpStatusCode.PartialContent,
            HttpStatusCode.NotModified, HttpStatusCode.UseProxy, HttpStatusCode.UpgradeRequired,
            HttpStatusCode.NoContent -> colored(
                status,
                Ansi.Color.YELLOW
            )
            else -> colored(status, Ansi.Color.RED)
        }
    }

    private fun colored(value: Any, color: Ansi.Color): String =
        if (isColorsEnabled) Ansi.ansi().fg(color).a(value).reset().toString()
        else value.toString() // ignore color
}

package io.ktor.openapi

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.reportLog
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.bufferedWriter

fun interface Logger {
    companion object {
        const val DEBUG_LOG_FILE = "ktor-compiler-debug.log"
        private val LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS: ")

        fun wrap(configuration: CompilerConfiguration, debug: Boolean, logDir: String?): Logger {
            val debugFileWriter = if (debug && logDir != null) {
                Paths.get(logDir, DEBUG_LOG_FILE).bufferedWriter().also {
                    it.append(LocalDateTime.now().format(LOG_FORMATTER))
                    it.appendLine("Ktor compiler plugin enabled in debug mode.")
                    it.flush()
                }
            } else null

            return Logger { message, cause, location ->
                // Always log messages via the compiler configuration's reporter
                val fullMessage = if (debug && cause != null) {
                    "$message\n${cause.stackTraceToString()}"
                } else message

                configuration.reportLog(fullMessage, location)

                debugFileWriter?.appendLine(buildString {
                    append(LocalDateTime.now().format(LOG_FORMATTER))
                    append(fullMessage)
                })
                debugFileWriter?.flush()
            }
        }
    }

    fun log(message: String) = log(message, cause = null, location = null)
    fun log(message: String, cause: Throwable?) = log(message, cause, location = null)
    fun log(message: String, cause: Throwable?, location: CompilerMessageLocation?)
}

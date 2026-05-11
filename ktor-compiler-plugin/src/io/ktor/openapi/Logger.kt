package io.ktor.openapi

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.appendText
import kotlin.io.path.bufferedWriter

fun interface Logger {
    companion object {
        const val DEBUG_LOG_FILE = "ktor-compiler-debug.log"
        private val LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS: ")

        fun wrap(messageCollector: MessageCollector, debug: Boolean, logDir: String?): Logger {
            val debugFileWriter = if (debug && logDir != null) {
                Paths.get(logDir, DEBUG_LOG_FILE).bufferedWriter().also {
                    it.append(LocalDateTime.now().format(LOG_FORMATTER))
                    it.appendLine("Ktor compiler plugin enabled in debug mode.")
                    it.flush()
                }
            } else null

            return Logger { message, cause, location ->
                // Always log messages to message collector
                val fullMessage = if (debug && cause != null) {
                    "$message\n${cause.stackTraceToString()}"
                } else message

                messageCollector.report(
                    severity = CompilerMessageSeverity.LOGGING,
                    message = fullMessage,
                    location = location,
                )

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
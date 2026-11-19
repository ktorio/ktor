/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import kotlinx.coroutines.*
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the test server in a separate JVM process, so its responsiveness doesn't depend on the Gradle daemon's GC.
 */
abstract class TestServerService : BuildService<TestServerService.Parameters>, AutoCloseable {

    private val logger = Logging.getLogger(TestServerService::class.java)
    private val logFile = parameters.logFile.get().asFile
    private val scope = CoroutineScope(Dispatchers.IO)
    private var process: Process? = null
    @Volatile
    private var stopping = false

    @Synchronized
    fun start(libraries: File) {
        if (process == null) process = runBlocking { startServer(libraries) }
    }

    private suspend fun startServer(libraries: File): Process {
        logger.lifecycle("Starting test server...")
        val process = startProcess(libraries)
        try {
            val ready = CompletableDeferred<Boolean>()
            scope.launch(CoroutineName("test-server-output")) { process.listenOutput(ready) }
            process.awaitReady(ready)
        } catch (cause: Throwable) {
            process.stop()
            throw cause
        }
        logger.lifecycle("Test server started (pid ${process.pid()}).")
        return process
    }

    private fun startProcess(libraries: File): Process {
        logFile.parentFile.mkdirs()
        val command = buildList {
            add(ProcessHandle.current().info().command().orElseThrow())
            addAll(JVM_ARGS)
            add("-XX:HeapDumpPath=${logFile.parent}")
            add("-Dktor.testServer.readyMarker=$READY_MARKER")
            add("-cp")
            add(libraries.resolve("*").path)
            add(MAIN_CLASS)
            if (parameters.verbose.get()) add("--verbose")
        }

        logger.info("Test server command: {}", command.joinToString(" "))
        return ProcessBuilder(command).redirectErrorStream(true).start()
    }

    private fun Process.listenOutput(ready: CompletableDeferred<Boolean>) {
        val verbose = parameters.verbose.get()
        logFile.bufferedWriter().use { writer ->
            inputStream.bufferedReader().forEachLine { line ->
                writer.appendLine(line)
                writer.flush()
                if (verbose) logger.lifecycle("[test-server] $line")
                if (line == READY_MARKER) ready.complete(true)
            }
        }
        val exitedAfterStart = !ready.complete(false)
        if (exitedAfterStart && !stopping) {
            logger.warn("Test server exited unexpectedly with code ${waitFor()}. See the log: $logFile")
        }
    }

    private suspend fun Process.awaitReady(ready: Deferred<Boolean>) {
        val isReady = withTimeoutOrNull(START_TIMEOUT) { ready.await() }
        checkNotNull(isReady) { "Test server didn't start within $START_TIMEOUT.\n${logTail()}" }
        check(isReady) { "Test server exited with code ${waitFor()}.\n${logTail()}" }
    }

    private fun logTail(): String {
        val lines = if (logFile.exists()) logFile.readLines().takeLast(50) else emptyList()
        return "Server log ($logFile):\n" + lines.joinToString("\n")
    }

    @Synchronized
    override fun close() {
        val process = process ?: return
        logger.lifecycle("Stopping test server...")
        stopping = true
        process.stop()
        scope.cancel()
        logger.lifecycle("Test server stopped.")
    }

    private fun Process.stop() {
        destroy()
        if (!waitFor(5, TimeUnit.SECONDS)) {
            destroyForcibly().waitFor(5, TimeUnit.SECONDS)
        }
    }

    interface Parameters : BuildServiceParameters {
        val verbose: Property<Boolean>
        val logFile: RegularFileProperty
    }

    companion object {
        // Should be synced with `application.mainClass` in server/build.gradle.kts
        private const val MAIN_CLASS = "test.server.TestServerMainKt"
        private const val READY_MARKER = "Test server is ready"
        // Should be synced with the `testServerLibraries` configuration in server/build.gradle.kts
        private const val SERVER_LIBRARIES_USAGE = "ktor-test-server-libraries"
        // [State on 19.11.2026]
        // Live heap after full GC peaks at ~60 MB during JVM client tests, process RSS at ~280 MB.
        // SerialGC avoids G1's native overhead (~60 MB at this heap size).
        private val JVM_ARGS = listOf(
            "-Xmx128m",
            "-XX:+UseSerialGC",
            // Keep the heap for investigation and fail fast instead of serving tests from a broken state
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:+ExitOnOutOfMemoryError",
        )
        private val START_TIMEOUT = 60.seconds

        fun registerIfAbsent(project: Project): Provider<TestServerService> {
            val verbose = project.providers.gradleProperty("ktorbuild.testServer.verbose")
                .map { it.toBoolean() }
                .orElse(false)
            return project.gradle.sharedServices.registerIfAbsent("test-server", TestServerService::class) {
                parameters.verbose.set(verbose)
                parameters.logFile.set(
                    project.isolated.rootProject.projectDirectory.file("build/test-server/test-server.log")
                )
            }
        }

        /** Use it as a task input, so Gradle builds the server before the task runs. */
        fun serverLibraries(project: Project): FileCollection {
            val dependency = project.dependencies.create("io.ktor.test:server")
            val configuration = project.configurations.detachedConfiguration(dependency).apply {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(SERVER_LIBRARIES_USAGE))
                }
            }
            return configuration.incoming.files
        }
    }
}

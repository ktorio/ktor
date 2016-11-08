package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.net.*


@State(Scope.Benchmark)
open class IntegrationBenchmark {
    private val packageName = FullBenchmark::class.java.`package`.name
    private val classFileName = FullBenchmark::class.simpleName!! + ".class"
    private val pomFile = File("pom.xml")

    lateinit private var server: JettyApplicationHost

    private val port = 5678

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level = Level.ERROR
        server = embeddedJettyServer(port) {
            routing {
                get("/sayOK") {
                    call.respond("OK")
                }
                get("/jarfile") {
                    call.respond(call.resolveClasspathWithPath("java/lang/", "String.class")!!)
                }
                get("/regularClasspathFile") {
                    call.respond(call.resolveClasspathWithPath(packageName, classFileName)!!)
                }
                get("/regularFile") {
                    call.respond(LocalFileContent(pomFile))
                }
            }
        }
        server.start()
    }

    @TearDown
    fun shutdownServer() {
        server.stop()
    }

    @Benchmark
    fun sayOK(): String {
        return URL("http://localhost:$port/sayOK").readText()
    }
}

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 8
        run<IntegrationBenchmark>()
    }
}



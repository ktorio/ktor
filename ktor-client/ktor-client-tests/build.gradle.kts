/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.net.*

description = "Common tests for client"

val ideaActive: Boolean by project.extra

plugins {
    kotlin("plugin.serialization")
}

open class KtorTestServer : DefaultTask() {
    @Internal
    var server: Closeable? = null
        private set

    @Internal
    lateinit var main: String

    @Internal
    lateinit var classpath: FileCollection

    @TaskAction
    fun exec() {
        try {
            println("[TestServer] start")
            val urlClassLoaderSource = classpath.map { file -> file.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(main)
            val main = mainClass.getMethod("startServer")
            server = main.invoke(null) as Closeable
            println("[TestServer] started")
        } catch (cause: Throwable) {
            println("[TestServer] failed: ${cause.message}")
            cause.printStackTrace()
        }
    }
}

val osName = System.getProperty("os.name")

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization"))
        }
    }
    val commonTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-features:ktor-client-auth"))
            api(project(":ktor-client:ktor-client-features:ktor-client-encoding"))
        }
    }
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-features:ktor-auth"))
            api(project(":ktor-features:ktor-websockets"))
            api(project(":ktor-features:ktor-serialization"))
            api(libs.logback.classic)
            api(libs.junit)
            api(libs.kotlin.test.junit)
            api(libs.kotlinx.coroutines.debug)
        }
    }

    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-apache"))
            runtimeOnly(project(":ktor-client:ktor-client-cio"))
            runtimeOnly(project(":ktor-client:ktor-client-android"))
            runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
            if (project.ext["currentJdk"] as Int >= 11) {
                runtimeOnly(project(":ktor-client:ktor-client-java"))
            }
        }
    }

    val jsTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }
}

val startTestServer = task<KtorTestServer>("startTestServer") {
    dependsOn(tasks["jvmJar"])

    main = "io.ktor.client.tests.utils.TestServerKt"
    val kotlinCompilation = kotlin.targets.getByName("jvm").compilations["test"]
    classpath = (kotlinCompilation as KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles
}

val testTasks = mutableListOf(
    "jvmTest",

    // 1.4.x JS tasks
    "jsLegacyNodeTest",
    "jsIrNodeTest",
    "jsLegacyBrowserTest",
    "jsIrBrowserTest",

    "posixTest",
    "darwinTest"
)

if (!ideaActive) {
    testTasks += listOf(
        "macosX64Test",
        "linuxX64Test",
        "iosX64Test",
        "mingwX64Test"
    )
}

rootProject.allprojects {
    if (!path.contains("ktor-client")) {
        return@allprojects
    }
    val tasks = tasks.matching { it.name in testTasks }
    configure(tasks) {
        dependsOn(startTestServer)
        kotlin.sourceSets {
            if (ideaActive) {
                if (name == "posixTest") {
                    getByName(name) {
                        dependencies {
                            val hostname: String by project.ext
                            api(project(":ktor-client:ktor-client-curl"))

                            if (!hostname.startsWith("win")) {
                                api(project(":ktor-client:ktor-client-cio"))
                            }
                        }
                    }
                }
                return@sourceSets
            }
            if (name in listOf("macosX64Test", "linuxX64Test")) {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-curl"))
                        api(project(":ktor-client:ktor-client-cio"))
                    }
                }
            }
            if (name == "iosX64Test") {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-cio"))
                    }
                }
            }
            if (name == "mingwX64Test") {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-curl"))
                    }
                }
            }
        }
    }
}

useJdkVersionForJvmTests(11)

gradle.buildFinished {
    if (startTestServer.server != null) {
        startTestServer.server?.close()
        println("[TestServer] stop")
    }
}

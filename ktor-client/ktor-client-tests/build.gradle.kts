/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.net.*

description = "Common tests for client"

val junit_version: String by project.extra
val kotlin_version: String by project.extra
val logback_version: String by project.extra
val coroutines_version: String by project

val ideaActive: Boolean by project.extra

plugins {
    id("kotlinx-serialization")
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
            api("ch.qos.logback:logback-classic:$logback_version")
            api("junit:junit:$junit_version")
            api("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
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

    if (rootProject.ext.get("native_targets_enabled") as Boolean) {
        if (!ideaActive) {
            listOf("linuxX64Test", "mingwX64Test", "macosX64Test").map { getByName(it) }.forEach {
                it.dependencies {
                    api(project(":ktor-client:ktor-client-curl"))
                }
            }

            if (!osName.startsWith("Windows")) {
                listOf("linuxX64Test", "macosX64Test", "iosX64Test").map { getByName(it) }.forEach {
                    it.dependencies {
                        api(project(":ktor-client:ktor-client-cio"))
                    }
                }
            }
            listOf("iosX64Test", "macosX64Test").map { getByName(it) }.forEach {
                it.dependencies {
                    // api(project(":ktor-client:ktor-client-ios"))
                }
            }
        } else {
            val posixTest by getting {
                dependencies {
                    val hostname: String by project.ext
                    // api(project(":ktor-client:ktor-client-ios"))
                    api(project(":ktor-client:ktor-client-curl"))

                    if (!hostname.startsWith("win")) {
                        api(project(":ktor-client:ktor-client-cio"))
                    }
                }
            }
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
    if (path.contains("ktor-client")) {
        val tasks = tasks.matching { it.name in testTasks }
        configure(tasks) {
            dependsOn(startTestServer)
        }
    }
}

gradle.buildFinished {
    if (startTestServer.server != null) {
        startTestServer.server?.close()
        println("[TestServer] stop")
    }
}

// TODO: this test is failing on JVM IR
if (rootProject.ext.get("jvm_ir_enabled") as Boolean) {
    tasks.named<Test>("jvmTest") {
        filter {
            excludeTest("io.ktor.client.tests.MultithreadedTest", "numberTest")
        }
    }
}

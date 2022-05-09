/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.net.*

description = "Common tests for client"

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
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-test-dispatcher"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-auth"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-encoding"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
        }
    }
    jvmMain {
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.logback.classic)
            api(libs.junit)
            api(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-apache"))
            runtimeOnly(project(":ktor-client:ktor-client-cio"))
            runtimeOnly(project(":ktor-client:ktor-client-android"))
            runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
            if (currentJdk >= 11) {
                runtimeOnly(project(":ktor-client:ktor-client-java"))
            }
            implementation(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            implementation(libs.kotlinx.coroutines.slf4j)
        }
    }

    jsTest {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }

    if (rootProject.ext.get("native_targets_enabled") as Boolean) {
        listOf("linuxX64Test", "mingwX64Test", "macosX64Test", "macosArm64Test").map { getByName(it) }.forEach {
            it.dependencies {
                api(project(":ktor-client:ktor-client-curl"))
            }
        }

        if (!osName.startsWith("Windows")) {
            listOf("linuxX64Test", "macosX64Test", "iosX64Test", "macosArm64Test").map { getByName(it) }.forEach {
                it.dependencies {
                    api(project(":ktor-client:ktor-client-cio"))
                }
            }
        }
        listOf("iosX64Test", "macosX64Test", "macosArm64Test").map { getByName(it) }.forEach {
            it.dependencies {
                api(project(":ktor-client:ktor-client-darwin"))
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

testTasks += listOf(
    "macosX64Test",
    "macosArm64Test",
    "linuxX64Test",
    "iosX64Test",
    "mingwX64Test"
)

rootProject.allprojects {
    if (!path.contains("ktor-client") || path.contains("ktor-shared")) return@allprojects

    val tasks = tasks.matching { it.name in testTasks }
    configure(tasks) {
        dependsOn(startTestServer)
        kotlin.sourceSets {
            if (!(rootProject.ext.get("native_targets_enabled") as Boolean)) return@sourceSets

            if (name in listOf("macosX64Test", "linuxX64Test", "mingwX64Test", "macosArm64Test")) {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-curl"))
                    }
                }
            }
            if (name in listOf("macosX64Test", "linuxX64Test", "iosX64Test", "macosArm64Test")) {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-cio"))
                    }
                }
            }
            if (name in listOf("macosX64Test", "iosX64Test", "macosArm64Test")) {
                getByName(name) {
                    dependencies {
                        api(project(":ktor-client:ktor-client-darwin"))
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

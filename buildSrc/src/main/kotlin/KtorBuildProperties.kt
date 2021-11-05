/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

private val java_version: String = System.getProperty("java.version", "8.0.0")

private val versionComponents = java_version
    .split(".")
    .take(2)
    .filter { it.isNotBlank() }
    .map { Integer.parseInt(it) }

val IDEA_ACTIVE: Boolean = System.getProperty("idea.active") == "true"

val OS_NAME = System.getProperty("os.name").toLowerCase()

val HOST_NAME = when {
    OS_NAME.startsWith("linux") -> "linux"
    OS_NAME.startsWith("windows") -> "windows"
    OS_NAME.startsWith("mac") -> "macos"
    else -> error("Unknown os name `$OS_NAME`")
}

val MAC_TARGETS = setOf(
    "macosX64",
    "macosArm64",
    "iosX64",
    "iosArm64",
    "iosArm32",
    "iosSimulatorArm64",
    "watchosX86",
    "watchosX64",
    "watchosArm32",
    "watchosArm64",
    "watchosSimulatorArm64",
    "tvosX64",
    "tvosArm64",
    "tvosSimulatorArm64",
)

val WIN_TARGETS = setOf("mingwX64")

val LINUX_TARGETS = setOf("linuxX64")

val EXCLUDE_MAP = mapOf(
    "linux" to MAC_TARGETS + WIN_TARGETS,
    "windows" to MAC_TARGETS + LINUX_TARGETS,
    "macos" to WIN_TARGETS + LINUX_TARGETS
)

object KtorBuildProperties {
    val jettyAlpnBootVersion: String? = when (java_version) {
        "1.8.0_191",
        "1.8.0_192",
        "1.8.0_201",
        "1.8.0_202",
        "1.8.0_211",
        "1.8.0_212",
        "1.8.0_221",
        "1.8.0_222",
        "1.8.0_231",
        "1.8.0_232",
        "1.8.0_241",
        "1.8.0_242" -> "8.1.13.v20181017"
        else -> null
    }

    @JvmStatic
    fun posixTargets(project: Project): Set<KotlinNativeTarget> = project.kotlin.posixTargets()

    @JvmStatic
    val currentJdk = if (versionComponents[0] == 1) versionComponents[1] else versionComponents[0]

    val jdk8Modules = listOf(
        "ktor-client-tests",

        "ktor-server-core", "ktor-server-host-common", "ktor-server-servlet", "ktor-server-netty", "ktor-server-tomcat",
        "ktor-server-test-host", "ktor-server-test-suites",

        "ktor-websockets", "ktor-webjars", "ktor-metrics", "ktor-server-sessions", "ktor-auth", "ktor-auth-jwt",

        "ktor-network-tls-certificates"
    )

    val jdk7Modules = listOf(
        "ktor-http",
        "ktor-utils",
        "ktor-network-tls",
        "ktor-websockets"
    )

    val jdk11Modules = listOf(
        "ktor-client-java"
    )
}

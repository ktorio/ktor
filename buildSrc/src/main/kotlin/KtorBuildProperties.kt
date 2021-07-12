import KtorBuildProperties.jdk8Modules
import org.gradle.api.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

private val java_version: String = System.getProperty("java.version", "8.0.0")

private val versionComponents = java_version
    .split(".")
    .take(2)
    .filter { it.isNotBlank() }
    .map { Integer.parseInt(it) }

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
    val ideaActive: Boolean = System.getProperty("idea.active") == "true"

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

    @JvmStatic
    fun projectJdk(name: String): Int = when (name) {
        in jdk8Modules -> 8
        in jdk11Modules -> 11
        in jdk7Modules -> 7
        else -> 6
    }
}

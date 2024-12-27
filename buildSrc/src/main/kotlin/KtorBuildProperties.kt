/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.JavaVersion
import org.gradle.api.Project

val IDEA_ACTIVE: Boolean = System.getProperty("idea.active") == "true"

val OS_NAME = System.getProperty("os.name").lowercase()

val HOST_NAME = when {
    OS_NAME.startsWith("linux") -> "linux"
    OS_NAME.startsWith("windows") -> "windows"
    OS_NAME.startsWith("mac") -> "macos"
    else -> error("Unknown os name `$OS_NAME`")
}

private var _testJdk = 0

/**
 * Retrieves the JDK version for running tests.
 *
 * Takes the version from property "test.jdk" or uses Gradle JDK by default.
 * For example, to run tests against JDK 8, run test task with flag "-Ptest.jdk=8"
 * or put this property to `gradle.properties`.
 */
val Project.testJdk: Int
    get() {
        if (_testJdk == 0) {
            _testJdk = rootProject.properties["test.jdk"]?.toString()?.toInt()
                ?: JavaVersion.current().majorVersion.toInt()
            logger.info("Running tests against JDK $_testJdk")
        }
        return _testJdk
    }

val Project.requiredJdkVersion: Int
    get() = when {
        name in jdk17Modules -> 17
        name in jdk11Modules -> 11
        else -> 8
    }

private val jdk17Modules = setOf(
    "ktor-server-jte",
)

private val jdk11Modules = setOf(
    "ktor-client-java",
    "ktor-client-jetty-jakarta",
    "ktor-server-jetty-jakarta",
    "ktor-server-jetty-test-http2-jakarta",
    "ktor-server-openapi",
    "ktor-server-servlet-jakarta",
    "ktor-server-tomcat-jakarta",
)

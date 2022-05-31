/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
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

val currentJdk = if (versionComponents[0] == 1) versionComponents[1] else versionComponents[0]

val jdk11Modules = listOf(
    "ktor-client-java"
)

fun Project.useJdkVersionForJvmTests(version: Int) {
    tasks.getByName("jvmTest").apply {
        check(this is Test)

        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(version))
            }
        )
    }
}

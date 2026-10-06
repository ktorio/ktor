/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("ktorbuild.kdoc.MainKt")
}

dependencies {
    implementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val excludedPathPatterns = listOf(
    ".gradle/**",
    "build-logic/**",
    "build-settings-logic/**",
    "ktor-test-server/**",
    "**/build/**",
)

tasks.run.configure {
    args(rootProject.projectDir.parentFile, "https://ktor.io/feedback/")
    args(excludedPathPatterns.map { "--exclude=$it" })
}

kotlin {
    jvmToolchain(21)
}

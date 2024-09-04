/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*

fun Project.posixTargets(): List<String> = nixTargets() + windowsTargets()

fun Project.nixTargets(): List<String> = darwinTargets() + linuxTargets()

fun Project.linuxTargets(): List<String> = with(kotlin) {
    listOf(
        linuxX64(),
        linuxArm64(),
    )
}.map { it.name }

fun Project.darwinTargets(): List<String> = macosTargets() + iosTargets() + watchosTargets() + tvosTargets()

fun Project.macosTargets(): List<String> = with(kotlin) {
    listOf(
        macosX64(),
        macosArm64()
    ).map { it.name }
}

fun Project.iosTargets(): List<String> = with(kotlin) {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).map { it.name }
}

fun Project.watchosTargets(): List<String> = with(kotlin) {
    listOfNotNull(
        watchosX64(),
        watchosArm32(),
        watchosArm64(),
        watchosSimulatorArm64(),
        // ktor-server-config-yaml: because of dependency on YAML library: https://github.com/Him188/yamlkt/issues/67
        // ktor-serialization-kotlinx-xml: because of dependency on xmlutil library: https://repo.maven.apache.org/maven2/io/github/pdvrieze/xmlutil/ // ktlint-disable max-line-length
        if ((project.name != "ktor-server-config-yaml") && (project.name != "ktor-serialization-kotlinx-xml")) {
            watchosDeviceArm64()
        } else {
            null
        },
    ).map { it.name }
}

fun Project.tvosTargets(): List<String> = with(kotlin) {
    listOf(
        tvosX64(),
        tvosArm64(),
        tvosSimulatorArm64(),
    ).map { it.name }
}

fun Project.desktopTargets(): List<String> = with(kotlin) {
    listOf(
        macosX64(),
        macosArm64(),
        linuxX64(),
        linuxArm64(),
        mingwX64()
    ).map { it.name }
}

fun Project.windowsTargets(): List<String> = with(kotlin) {
    listOf(
        mingwX64()
    ).map { it.name }
}

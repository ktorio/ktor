/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.utils.addToStdlib.*
import java.util.*

plugins {
    `kotlin-dsl`
}

val cacheRedirectorEnabled = System.getenv("CACHE_REDIRECTOR_ENABLED")?.toBoolean() == true
val buildSnapshotTrain = properties["build_snapshot_train"]?.toString()?.toBoolean() == true

repositories {
    if (cacheRedirectorEnabled) {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
    }

    maven("https://plugins.gradle.org/m2")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

    if (buildSnapshotTrain) {
        mavenLocal()
    }
}

val props = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}

fun version(target: String): String {
    // Intercept reading from properties file
    if (target == "kotlin") {
        val snapshotVersion = properties["kotlin_snapshot_version"]
        if (snapshotVersion != null) return snapshotVersion.toString()
    }
    return properties["${target}_version"].safeAs<String>() ?: props.getProperty("${target}_version")
}

sourceSets.main {
}

dependencies {
    println("Used kotlin version in buildSrc: " + version("kotlin"))
    implementation(kotlin("gradle-plugin", version("kotlin")))
    implementation("com.moowork.gradle:gradle-node-plugin:1.3.1")
    implementation("org.jmailen.gradle:kotlinter-gradle:${version("ktlint")}")
}

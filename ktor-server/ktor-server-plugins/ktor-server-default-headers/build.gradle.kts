/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}

val configuredVersion: String by rootProject.extra

val writeKtorVersionFileForNativeTask by tasks.registering {
    File("$projectDir/nix/resources/ktor-version.txt").apply {
        parentFile.mkdirs()
        createNewFile()
    }.writer().buffered().use { writer ->
        writer.write(configuredVersion)
    }
}

tasks.filter { it.name.endsWith("ProcessResources") && !it.name.contains("test") }.forEach {
    it.dependsOn(writeKtorVersionFileForNativeTask)
}

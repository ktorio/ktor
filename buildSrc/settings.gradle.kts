/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
pluginManagement {
    val build_snapshot_train: String? by settings
    repositories {
        val cacheRedirectorEnabled = System.getenv("CACHE_REDIRECTOR_ENABLED")?.toBoolean() == true
        if (cacheRedirectorEnabled) {
            println("Redirecting repositories for buildSrc buildscript")
            maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        }

        maven("https://plugins.gradle.org/m2")
        if (build_snapshot_train?.toBoolean() == true) {
            mavenLocal()
        }
    }
}

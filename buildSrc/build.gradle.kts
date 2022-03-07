/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://plugins.gradle.org/m2")
}

dependencies {
    implementation(kotlin("gradle-plugin", libs.versions.kotlin.version.get()))
}

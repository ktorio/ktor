/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import com.android.build.api.dsl.*

plugins {
    id("com.android.test")
    kotlin("multiplatform")
}

extensions.getByType(TestExtension::class.java).apply {
    compileSdk = 34
}

kotlin {
    androidTarget()
}

android {
    namespace = "io.ktor.client.engine.android"

    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 9

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
    implementation(project(":ktor-client:ktor-client-android"))
}

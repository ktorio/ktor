/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    id("com.android.library")
}

//kotlin {
//    androidTarget {
//        publishLibraryVariants("release")
//        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
//    }
//
//    sourceSets {
//        val androidTest by getting {
////            kotlin.srcDir("android/test")
//            dependencies {
//                implementation(project(":ktor-client:ktor-client-android"))
//                implementation(kotlin("test-junit5"))
//            }
//        }
//    }
//
//    sourceSets.all {
//    }
//}

android {
    namespace = "io.ktor.client.engine.android"

    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 9
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
}

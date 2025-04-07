import jdk.tools.jlink.resources.plugins

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

repositories {
}

plugins {
    id("com.android.library")
    `maven-publish`
}

kotlin {
    sourceSets {
        androidTarget {
//            namespace = "io.ktor.webrtc.client.android"
//            compileSdk = 34
//
//            defaultConfig {
//                applicationId = "io.ktor.webrtc.client.android"
//                minSdk = 24
//                targetSdk = 34
//                versionCode = 1
//                versionName = "1.0"
//            }
//
//            buildTypes {
//                release {
//                    isMinifyEnabled = false
//                    proguardFiles(
//                        getDefaultProguardFile("proguard-android-optimize.txt"),
//                        "proguard-rules.pro"
//                    )
//                }
//            }
//
//            compileOptions {
//                sourceCompatibility = JavaVersion.VERSION_17
//                targetCompatibility = JavaVersion.VERSION_17
//            }
//        }
        }
    }
}

//dependencies {
//    implementation("androidx.core:core-ktx:1.12.0")
//    implementation("androidx.appcompat:appcompat:1.6.1")
//}

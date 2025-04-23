import org.jetbrains.kotlin.gradle.idea.tcs.extras.isNativeStdlibKey

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("com.android.library")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        androidMain {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
                implementation(libs.stream.webrtc.android)
            }
        }

        androidUnitTest {
            dependencies {
                implementation("org.robolectric:robolectric:4.14")
            }
        }

        androidInstrumentedTest.dependencies {
        }
    }
}

android {
    namespace = "io.ktor.webrtc.client"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isNativeStdlibKey
        }
    }
}

dependencies {
    implementation(libs.core)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":ktor-test-dispatcher"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.1.20")
}

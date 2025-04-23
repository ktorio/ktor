///*
// * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
// */
//
//@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
//
//import ktorbuild.internal.*
//import ktorbuild.targets.*
//import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
//import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
//
////plugins {
////    id("ktorbuild.base")
////    kotlin("multiplatform")
////    id("com.android.library")
////    id("org.jetbrains.kotlinx.atomicfu")
////    id("ktorbuild.codestyle")
////}
//
//kotlin {
//    explicitApi()
//
//    compilerOptions {
//        progressiveMode = ktorBuild.kotlinLanguageVersion.map { it >= KotlinVersion.DEFAULT }
//        apiVersion = ktorBuild.kotlinApiVersion
//        languageVersion = ktorBuild.kotlinLanguageVersion
//        freeCompilerArgs.addAll("-Xexpect-actual-classes")
//    }
//    applyHierarchyTemplate(KtorTargets.hierarchyTemplate)
//
//    androidTarget {
//        publishLibraryVariants("release")
//    }
//
//    sourceSets {
//        androidMain
//
//        androidUnitTest
//
//        androidInstrumentedTest
//    }
//}
//
//android {
//    compileSdk = 35
//
//    defaultConfig {
//        minSdk = 24
//    }
//    packaging {
//        resources {
//            excludes += "/META-INF/{AL2.0,LGPL2.1}"
//        }
//    }
//    buildTypes {
//        getByName("release") {
//            isMinifyEnabled = false
//        }
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_17
//        targetCompatibility = JavaVersion.VERSION_17
//    }
//}
//
//setupTrain()
//if (ktorBuild.isCI.get()) configureTestTasksOnCi()
//

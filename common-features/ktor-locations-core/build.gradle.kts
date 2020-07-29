/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("plugin.serialization")
}

val serialization_version: String by project.extra
description = ""

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version")
        }
    }

    val jvmMain by getting {
        dependencies {
            api(kotlin("reflect"))
        }
    }

    val jsMain by getting {
        dependencies {
        }
    }

    val jsTest by getting {
    }

    val posixMain by getting {
        dependencies {
        }
    }

}

kotlin {
    configure(sourceSets) {
        languageSettings.useExperimentalAnnotation("io.ktor.locations.KtorExperimentalLocationsAPI")
    }

    sourceSets.matching { it.name.contains("Main") }.all {
        project.ext.set("kotlin.mpp.freeCompilerArgsForSourceSet.${this.name}", "-Xexplicit-api=warning")
    }
}

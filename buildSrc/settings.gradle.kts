/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
pluginManagement {
    val build_snapshot_train: String? by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (build_snapshot_train.toBoolean()) {
            mavenLocal()
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"

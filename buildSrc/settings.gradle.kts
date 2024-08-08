import java.io.*
import java.util.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
pluginManagement {
    val build_snapshot_train: String? by settings
    val kotlin_repo_url: String? by settings

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        if (build_snapshot_train.toBoolean()) {
            mavenLocal()
        }
        if (kotlin_repo_url != null) {
            maven(kotlin_repo_url!!)
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        val libs by creating {
            from(files("../gradle/libs.versions.toml"))

            fun kotlinVersionFromProjectRootProperties(): String? {
                val properties = Properties()
                FileInputStream(file("../gradle.properties")).use {
                    properties.load(it)
                }
                return properties["kotlin_version"]?.toString()
            }

            val kotlinVersion = if (extra.has("kotlin_version")) {
                extra.get("kotlin_version").toString()
            } else {
                kotlinVersionFromProjectRootProperties()
            }

            if (kotlinVersion != null) {
                version("kotlin-version", kotlinVersion)
            }
        }
    }
}

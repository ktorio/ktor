/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*

plugins {
    id("ktorbuild.base")
    id("ktorbuild.dokka")
}

// Strip patch version (e.g., 3.0.3 -> 3.0.x, 3.3.0-rc.1 -> 3.3.x)
val apiVersion = project.version.toString()
    .replace(Regex("""^(\d+\.\d+)\..*$"""), "$1.x")

val dokkaVersionsDirectory = resolveVersionsDirectory()

dokka {
    moduleName = "Ktor"

    pluginsConfiguration {
        versioning {
            version = apiVersion
            if (dokkaVersionsDirectory != null) olderVersionsDir = dokkaVersionsDirectory
        }
    }

    dokkaPublications.html {
        if (dokkaVersionsDirectory != null) outputDirectory = dokkaVersionsDirectory.resolve(apiVersion)
    }
}

dependencies {
    dokkaHtmlPlugin(libs.dokka.plugin.versioning)
}

val libraryProjects = projectsWithTag(ProjectTag.Library, project.dependencies::create)

configurations.dokka {
    dependencies.addAllLater(libraryProjects)
}

fun resolveVersionsDirectory(): File? {
    val outputDirectory = providers.gradleProperty("ktor.dokka.versionsDirectory").orNull
    return outputDirectory?.let(rootDir::resolve)
}

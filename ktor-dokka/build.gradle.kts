/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import java.time.Year

plugins {
    id("ktorbuild.base")
    id("ktorbuild.dokka")
}

val projectVersion = project.version.toString()
val dokkaVersionsDirectory = resolveVersionsDirectory()

dokka {
    moduleName = "Ktor"

    pluginsConfiguration {
        html {
            customAssets.from("assets/logo-icon.svg")
            footerMessage = "© ${Year.now()} JetBrains s.r.o and contributors. Apache License 2.0"
        }

        versioning {
            version = projectVersion
            if (dokkaVersionsDirectory != null) olderVersionsDir = dokkaVersionsDirectory
        }
    }

    dokkaPublications.html {
        if (dokkaVersionsDirectory != null) outputDirectory = dokkaVersionsDirectory.resolve(projectVersion)
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

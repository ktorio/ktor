/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.base")
    id("ktorbuild.dokka")
}

dependencies {
    rootProject.subprojects.forEach { subproject ->
        if (subproject != project && subproject.hasDokka) dokka(subproject)
    }

    dokkaHtmlPlugin(libs.dokka.plugin.versioning)
}

val projectVersion = project.version.toString()
val dokkaVersionsDirectory = resolveVersionsDirectory()

dokka {
    pluginsConfiguration {
        versioning {
            version = projectVersion
            if (dokkaVersionsDirectory != null) olderVersionsDir = dokkaVersionsDirectory
        }
    }

    dokkaPublications.html {
        if (dokkaVersionsDirectory != null) outputDirectory = dokkaVersionsDirectory.dir(projectVersion)
    }
}

fun resolveVersionsDirectory(): Directory? {
    val outputDirectory = project.findProperty("ktor.dokka.versionsDirectory") as? String
    return outputDirectory?.let(rootProject.layout.projectDirectory::dir)
}

val Project.hasDokka: Boolean get() = plugins.hasPlugin("ktorbuild.dokka")

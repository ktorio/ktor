/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*

plugins {
    id("ktorbuild.base")
    id("version-catalog")
    id("ktorbuild.publish")
}

val publishedProjectsProvider = projectsWithTag(ProjectTag.Published, Project::getName)

// A hack to prevent all projects evaluation if version catalog generation wasn't requested
// Issue: https://github.com/gradle/gradle/issues/33568
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name == VersionCatalogPlugin.GENERATE_CATALOG_FILE_TASKNAME }) {
        catalog.versionCatalog {
            configureVersionCatalog(
                group = project.group.toString(),
                version = project.version.toString(),
                publishedProjects = publishedProjectsProvider.get().toSet(),
            )
        }
    }
}

fun VersionCatalogBuilder.configureVersionCatalog(
    group: String,
    version: String,
    publishedProjects: Set<String>,
) {
    // Versions
    val versionAlias = version("ktor", version)

    // Libraries
    for (projectName in publishedProjects - excludedProjects) {
        if (projectName == "ktor-version-catalog") continue
        library(
            /* alias = */ libraryAlias(projectName, publishedProjects + hierarchyRoots),
            /* group = */ group,
            /* artifact = */ projectName,
        ).versionRef(versionAlias)
    }
    library("gradlePlugin", "io.ktor.plugin", "plugin").versionRef(versionAlias)

    // Plugins
    plugin("ktor", "io.ktor.plugin").versionRef(versionAlias)
}

// Projects that are published but should be excluded from the version catalog.
// This means we don't want our users to use these dependencies.
val excludedProjects = setOf(
    "ktor-client",
    "ktor-server",
    "ktor-server-host-common",
)

val hierarchyRoots = setOf(
    "ktor",
    "ktor-client",
    "ktor-server",
)

// Handle special cases when automatic mapping doesn't work well
val manualOverrides = mapOf(
    // We want to preserve the hierarchy in case we add more config formats
    "ktor-server-config-yaml" to "server-config-yaml",
    // Fix a typo in the project name
    "ktor-websocket-serialization" to "websockets-serialization",
    // Prefer '*-jakarta' artifacts over the deprecated ones
    "ktor-client-jetty-jakarta" to "client-jetty",
    "ktor-client-jetty" to "client-jetty-legacy",
    "ktor-server-jetty-jakarta" to "server-jetty",
    "ktor-server-jetty" to "server-jetty-legacy",
    "ktor-server-servlet-jakarta" to "server-servlet",
    "ktor-server-servlet" to "server-servlet-legacy",
    "ktor-server-tomcat-jakarta" to "server-tomcat",
    "ktor-server-tomcat" to "server-tomcat-legacy",
)

/**
 * Generates a library alias for a given [projectName] to use in the version catalog.
 *
 * This function converts hierarchical project names into appropriately structured aliases.
 * For example, `ktor-client-content-negotiation` becomes `client-contentNegotiation`.
 */
fun libraryAlias(projectName: String, possiblePrefixes: Set<String>): String {
    if (projectName in manualOverrides) return manualOverrides.getValue(projectName)

    var prefix = projectName.substringBeforeLast("-")
    var suffix = projectName.substringAfterLast("-")

    while (prefix !in possiblePrefixes) {
        val lastWord = prefix.substringAfterLast("-")
        prefix = prefix.substringBeforeLast("-")
        suffix = lastWord + suffix.replaceFirstChar { it.uppercase() }
    }

    return if (prefix != "ktor") {
        "${libraryAlias(prefix, possiblePrefixes)}-$suffix"
    } else {
        suffix
    }
}

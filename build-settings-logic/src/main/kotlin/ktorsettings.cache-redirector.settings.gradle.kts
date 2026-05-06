/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.net.URI

// Copied from the Kotlin project
// Source: https://github.com/JetBrains/kotlin/blob/v2.3.10/repo/gradle-settings-conventions/cache-redirector/src/main/kotlin/cache-redirector.settings.gradle.kts

private val ProviderFactory.isCIRun: Provider<Boolean>
    get() = gradleProperty("teamcity").map { true }
        .orElse(environmentVariable("TEAMCITY_VERSION").map { true })
        .orElse(false)

/**
 * Enable cache redirector if explicitly set via project property,
 * or when running in CI environment.
 */
private val Settings.useCacheRedirector: Provider<Boolean>
    get() = providers
        .gradleProperty("ktorbuild.useCacheRedirector")
        .map { it.toBoolean() }
        // TODO re-enable when it's behaving
        .orElse(false)

// Repository override section

/**
 *  The list of repositories supported by cache redirector should be synced with the "Table of redirects" at https://cache-redirector.jetbrains.com
 *  To add a repository to the list, create an issue in the ADM project (example issue https://youtrack.jetbrains.com/issue/IJI-149)
 *  Or send a merge request to https://jetbrains.team/p/iji/repositories/Cache-Redirector/files/64b69490c54a2a900bb3dd21471f942270289a12/images/config-gen/src/main/kotlin/Config.kt
 */
val cacheMap: Map<String, String> = mapOf(
    "https://repo.maven.apache.org/maven2" to "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2",
    "https://plugins.gradle.org/m2" to "https://cache-redirector.jetbrains.com/plugins.gradle.org/m2",
    "https://dl.google.com/dl/android/maven2" to "https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2",
    "https://redirector.kotlinlang.org/maven/ktor-eap" to "https://cache-redirector.jetbrains.com/redirector.kotlinlang.org/maven/ktor-eap",
    "https://redirector.kotlinlang.org/maven/dev" to "https://cache-redirector.jetbrains.com/redirector.kotlinlang.org/maven/dev",
    "https://services.gradle.org/distributions" to "https://cache-redirector.jetbrains.com/services.gradle.org/distributions",
    "https://registry.yarnpkg.com" to "https://cache-redirector.jetbrains.com/registry.yarnpkg.com",
    "https://nodejs.org/dist" to "https://cache-redirector.jetbrains.com/nodejs.org/dist",
)

val aliases = mapOf(
    "https://repo1.maven.org/maven2" to "https://repo.maven.apache.org/maven2",
    "https://maven.google.com" to "https://dl.google.com/dl/android/maven2",
)

fun String.maybeRedirect(): String {
    val url = this.trimEnd('/')
    val deAliasedUrl = aliases.getOrDefault(url, url)
    val cacheUrlEntry = cacheMap.entries.find { (origin, _) -> deAliasedUrl.startsWith(origin) } ?: return this

    val cacheUrl = cacheUrlEntry.value
    val originRestPath = deAliasedUrl.substringAfter(cacheUrlEntry.key, "")
    return "$cacheUrl$originRestPath"
}

fun URI.maybeRedirect(): URI = URI(toString().maybeRedirect())

fun RepositoryHandler.redirect() = configureEach {
    when (this) {
        is MavenArtifactRepository -> url = url.maybeRedirect()
        is IvyArtifactRepository -> @Suppress("SENSELESS_COMPARISON") if (url != null) {
            url = url.maybeRedirect()
        }
    }
}

fun Project.overrideGradleDistributionUrl() {
    gradle.taskGraph.whenReady {
        tasks.named<Wrapper>("wrapper") {
            distributionUrl = distributionUrl.maybeRedirect()
        }
    }
}

// Native compiler download url override section

fun Project.overrideNativeCompilerDownloadUrl() {
    logger.info("Redirecting Kotlin/Native compiler download url")
    extensions.extraProperties["kotlin.native.distribution.baseDownloadUrl"] =
        "https://cache-redirector.jetbrains.com/download.jetbrains.com/kotlin/native/builds"
}

// Check repositories are overridden section
abstract class CheckRepositoriesTask : DefaultTask() {
    @get:Input
    val teamcityBuild = project.providers.isCIRun

    @get:Input
    val ivyNonCachedRepositories = project.providers.provider {
        project.repositories
            .filterIsInstance<IvyArtifactRepository>()
            .filter {
                @Suppress("SENSELESS_COMPARISON")
                it.url == null
            }
            .map { it.name }
    }

    @get:Input
    val nonCachedRepositories = project.providers.provider {
        project.repositories.findNonCachedRepositories()
    }

    @get:Input
    val nonCachedBuildscriptsRepositories = project.providers.provider {
        project.buildscript.repositories.findNonCachedRepositories()
    }

    @get:Internal
    val projectDisplayName = project.displayName

    @TaskAction
    fun checkRepositories() {
        val testName = "$name in $projectDisplayName"
        val isTeamcityBuild = teamcityBuild.get()
        if (isTeamcityBuild) {
            testStarted(testName)
        }

        ivyNonCachedRepositories.get().forEach { ivyRepoName ->
            logInvalidIvyRepo(testName, projectDisplayName, isTeamcityBuild, ivyRepoName)
        }

        nonCachedRepositories.get().forEach { repoUrl ->
            logNonCachedRepo(testName, projectDisplayName, repoUrl, isTeamcityBuild)
        }

        nonCachedBuildscriptsRepositories.get().forEach { repoUrl ->
            logNonCachedRepo(testName, projectDisplayName, repoUrl, isTeamcityBuild)
        }

        if (isTeamcityBuild) {
            testFinished(testName)
        }
    }

    private fun URI.isCachedOrLocal() = scheme == "file" ||
        host == "cache-redirector.jetbrains.com" ||
        host == "teamcity.jetbrains.com" ||
        host == "buildserver.labs.intellij.net"

    private fun RepositoryHandler.findNonCachedRepositories(): List<String> {
        val mavenNonCachedRepos = filterIsInstance<MavenArtifactRepository>()
            .filterNot { it.url.isCachedOrLocal() }
            .map { it.url.toString() }

        val ivyNonCachedRepos = filterIsInstance<IvyArtifactRepository>()
            .filterNot { it.url.isCachedOrLocal() }
            .map { it.url.toString() }

        return mavenNonCachedRepos + ivyNonCachedRepos
    }

    private fun escape(s: String): String {
        return s.replace("[|'\\[\\]]".toRegex(), "\\|$0").replace("\n".toRegex(), "|n").replace("\r".toRegex(), "|r")
    }

    private fun testStarted(testName: String) {
        println("##teamcity[testStarted name='%s']".format(escape(testName)))
    }

    private fun testFinished(testName: String) {
        println("##teamcity[testFinished name='%s']".format(escape(testName)))
    }

    private fun testFailed(name: String, message: String, details: String) {
        println("##teamcity[testFailed name='%s' message='%s' details='%s']"
            .format(escape(name), escape(message), escape(details)))
    }

    private fun logNonCachedRepo(
        testName: String,
        projectDisplayName: String,
        repoUrl: String,
        isTeamcityBuild: Boolean
    ) {
        val msg = "Repository $repoUrl in $projectDisplayName should be cached with cache-redirector"
        val details = "Using non cached repository may lead to download failures in CI builds." +
            " Check https://github.com/ktorio/ktor/blob/main/build-settings-logic/src/main/kotlin/ktorsettings.cache-redirector.settings.gradle.kts for details."

        if (isTeamcityBuild) {
            testFailed(testName, msg, details)
        }

        logger.warn("WARNING - $msg\n$details")
    }

    private fun logInvalidIvyRepo(
        testName: String,
        projectDisplayName: String,
        isTeamcityBuild: Boolean,
        ivyRepoName: String,
    ) {
        val msg = "Invalid ivy repo found in $projectDisplayName"
        val details = "Url must be not null for $ivyRepoName repository"

        if (isTeamcityBuild) {
            testFailed(testName, msg, details)
        }

        logger.warn("WARNING - $msg: $details")
    }
}

fun Project.addCheckRepositoriesTask() {
    tasks.register("checkRepositories", CheckRepositoriesTask::class.java)
}

// Main configuration

if (useCacheRedirector.get()) {
    logger.info("Redirecting repositories for settings in ${settingsDir.absolutePath}")

    pluginManagement.repositories.redirect()
    dependencyResolutionManagement.repositories.redirect()
    buildscript.repositories.redirect()

    gradle.beforeProject {
        buildscript.repositories.redirect()
        repositories.redirect()
        overrideNativeCompilerDownloadUrl()
        addCheckRepositoriesTask()
    }
}

// Override Gradle distribution URL to use cache redirector.
// Must run unconditionally because the :wrapper task only generates the URL in gradle-wrapper.properties.
gradle.rootProject {
    overrideGradleDistributionUrl()
}

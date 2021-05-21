/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.gradle.api.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.artifacts.repositories.*
import org.gradle.api.initialization.dsl.*
import java.net.*


private val cacheRedirectorAvaialble: Boolean = isCacheRedirectorAvailable()

/**
 * Enabled via environment variable, so that it can be reliably accessed from any piece of the build script,
 * including buildSrc within TeamCity CI.
 */
private val cacheRedirectorEnabled =
    cacheRedirectorAvaialble && (System.getenv("CACHE_REDIRECTOR_ENABLED")?.toBoolean() == true)

/**
 *  The list of repositories supported by cache redirector should be synced with the list at https://cache-redirector.jetbrains.com/redirects_generated.html
 *  To add a repository to the list create an issue in ADM project (example issue https://youtrack.jetbrains.com/issue/IJI-149)
 */
private val mirroredUrls = listOf(
    "https://cdn.azul.com/zulu/bin",
    "https://clojars.org/repo",
    "https://dl.google.com/android/repository",
    "https://dl.google.com/dl/android/maven2",
    "https://dl.google.com/dl/android/studio/ide-zips",
    "https://dl.google.com/go",
    "https://download.jetbrains.com",
    "https://jitpack.io",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/eap",
    "https://oss.sonatype.org/content/repositories/releases",
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://oss.sonatype.org/content/repositories/staging",
    "https://packages.confluent.io/maven/",
    "https://plugins.gradle.org/m2",
    "https://plugins.jetbrains.com/maven",
    "https://repo1.maven.org/maven2",
    "https://repo.grails.org/grails/core",
    "https://repo.jenkins-ci.org/releases",
    "https://repo.maven.apache.org/maven2",
    "https://repo.spring.io/milestone",
    "https://repo.typesafe.com/typesafe/ivy-releases",
    "https://services.gradle.org",
    "https://www.exasol.com/artifactory/exasol-releases",
    "https://www.myget.org/F/intellij-go-snapshots/maven",
    "https://www.myget.org/F/rd-model-snapshots/maven",
    "https://www.myget.org/F/rd-snapshots/maven",
    "https://www.python.org/ftp",
    "https://www.jetbrains.com/intellij-repository/nightly",
    "https://www.jetbrains.com/intellij-repository/releases",
    "https://www.jetbrains.com/intellij-repository/snapshots",
    "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven"
)

private val aliases = mapOf(
    "https://repo.maven.apache.org/maven2" to "https://repo1.maven.org/maven2" // Maven Central
).onEach { (key, value) ->
    require(!key.endsWith('/'))
    require(!value.endsWith('/'))
}

private val CACHE_REDIRECTOR_URL = "https://cache-redirector.jetbrains.com/"
private fun URI.toCacheRedirectorUri() = URI("$CACHE_REDIRECTOR_URL$host$path")

private fun URI.maybeRedirect(): URI? {
    val url = toString().trimEnd('/')
    val dealiasedUrl = aliases.getOrDefault(url, url)

    return if (mirroredUrls.any { dealiasedUrl.startsWith(it) }) {
        URI(dealiasedUrl).toCacheRedirectorUri()
    } else {
        null
    }
}

private fun URI.isCachedOrLocal() = scheme == "file" ||
    host == "cache-redirector.jetbrains.com" ||
    host == "teamcity.jetbrains.com" ||
    host == "buildserver.labs.intellij.net"

private fun Project.checkRedirectUrl(url: URI, containerName: String): URI {
    val redirected = url.maybeRedirect()

    if (redirected == null && !url.isCachedOrLocal()) {
        val msg = "Repository $url in $containerName should be cached with cache-redirector"
        val details = "Using non cached repository may lead to download failures in CI builds." +
            " Check buildSrc/src/main/kotlin/CacheRedirector.kt for details."
        logger.warn("WARNING - $msg\n$details")
    }

    return if (cacheRedirectorEnabled) redirected ?: url else url
}

private fun Project.checkRedirect(repositories: RepositoryHandler, containerName: String) {
    if (cacheRedirectorEnabled) {
        logger.info("Redirecting repositories for $containerName")
    }

    for (repository in repositories) {
        when (repository) {
            is MavenArtifactRepository -> repository.url = checkRedirectUrl(repository.url, containerName)
            is IvyArtifactRepository -> repository.url = checkRedirectUrl(repository.url, containerName)
        }
    }
}

private fun isCacheRedirectorAvailable(): Boolean = try {
    val address = InetAddress.getByName(CACHE_REDIRECTOR_URL)
    (address.isReachable(10 * 1000))
} catch (_: Throwable) {
    false
}

// Used from Groovy scripts
object CacheRedirector {
    /**
     * Substitutes repositories in buildScript { } block.
     */
    @JvmStatic
    fun configureBuildScript(rootProject: Project, scriptHandler: ScriptHandler) {
        rootProject.checkRedirect(scriptHandler.repositories, "${rootProject.displayName} buildscript")
    }

    /**
     * Substitutes repositories in a project.
     */
    @JvmStatic
    fun configure(project: Project) {
        project.checkRedirect(project.repositories, project.displayName)
    }
}

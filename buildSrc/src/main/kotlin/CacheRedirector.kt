/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    cacheRedirectorAvaialble && (System.getenv("CACHE_REDIRECTOR_DISABLED")?.toBoolean() != true)

/**
 *  The list of repositories supported by cache redirector should be synced with the list at https://cache-redirector.jetbrains.com/redirects_generated.html
 *  To add a repository to the list create an issue in ADM project (example issue https://youtrack.jetbrains.com/issue/IJI-149)
 */
private val mirroredUrls = listOf(
    "https://cdn.azul.com/zulu/bin",
    "https://clojars.org/repo",
    "https://dl.bintray.com/d10xa/maven",
    "https://dl.bintray.com/groovy/maven",
    "https://dl.bintray.com/jetbrains/maven-patched",
    "https://dl.bintray.com/jetbrains/scala-plugin-deps",
    "https://dl.bintray.com/kodein-framework/Kodein-DI",
    "https://dl.bintray.com/konsoletyper/teavm",
    "https://dl.bintray.com/kotlin/kotlin-dev",
    "https://dl.bintray.com/kotlin/kotlin-eap",
    "https://dl.bintray.com/kotlin/kotlinx.html",
    "https://dl.bintray.com/kotlin/kotlinx",
    "https://dl.bintray.com/kotlin/ktor",
    "https://dl.bintray.com/scalacenter/releases",
    "https://dl.bintray.com/scalamacros/maven",
    "https://dl.bintray.com/kotlin/exposed",
    "https://dl.bintray.com/cy6ergn0m/maven",
    "https://dl.bintray.com/kotlin/kotlin-js-wrappers",
    "https://dl.google.com/android/repository",
    "https://dl.google.com/dl/android/maven2",
    "https://dl.google.com/dl/android/studio/ide-zips",
    "https://dl.google.com/go",
    "https://download.jetbrains.com",
    "https://jcenter.bintray.com",
    "https://jetbrains.bintray.com/dekaf",
    "https://jetbrains.bintray.com/intellij-jbr",
    "https://jetbrains.bintray.com/intellij-jdk",
    "https://jetbrains.bintray.com/intellij-plugin-service",
    "https://jetbrains.bintray.com/intellij-terraform",
    "https://jetbrains.bintray.com/intellij-third-party-dependencies",
    "https://jetbrains.bintray.com/jediterm",
    "https://jetbrains.bintray.com/kotlin-native-dependencies",
    "https://jetbrains.bintray.com/markdown",
    "https://jetbrains.bintray.com/teamcity-rest-client",
    "https://jetbrains.bintray.com/test-discovery",
    "https://jetbrains.bintray.com/wormhole",
    "https://jitpack.io",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap",
    "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/eap",
    "https://kotlin.bintray.com/dukat",
    "https://kotlin.bintray.com/kotlin-dependencies",
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
    "https://dl.bintray.com/jetbrains/kotlin-native-dependencies"
)

private val aliases = mapOf(
    "https://repo.maven.apache.org/maven2" to "https://repo1.maven.org/maven2", // Maven Central
    "https://kotlin.bintray.com/kotlin-dev" to "https://dl.bintray.com/kotlin/kotlin-dev",
    "https://kotlin.bintray.com/kotlin-eap" to "https://dl.bintray.com/kotlin/kotlin-eap",
    "https://kotlin.bintray.com/kotlinx" to "https://dl.bintray.com/kotlin/kotlinx"
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
    fun ScriptHandler.configureBuildScript(rootProject: Project) {
        rootProject.checkRedirect(repositories, "${rootProject.displayName} buildscript")
    }

    /**
     * Substitutes repositories in a project.
     */
    @JvmStatic
    fun Project.configure() {
        checkRedirect(repositories, displayName)
    }
}

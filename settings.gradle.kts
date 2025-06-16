/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Add repositories required for build-settings-logic
    repositories {
        gradlePluginPortal()

        // Should be in sync with ktorsettings.kotlin-user-project
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        if (kotlinRepoUrl != null) maven(kotlinRepoUrl) { name = "KotlinDev" }
    }

    includeBuild("build-settings-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("ktorsettings")
    id("ktorsettings.develocity")
    id("ktorsettings.configuration-cache")
}

rootProject.name = "ktor"

includeBuild("build-logic")
includeBuild("ktor-test-server")

// Project declarations go here.
// We use custom DSL instead of the default `include(":project:path")` function.
// - Use 'unaryPlus' operator to declare a project
// - Use 'including' keyword to declare nested projects
// - Declare projects in 'server', 'client' or 'shared' blocks when possible
projects {
    server {
        +"ktor-server-core"
        +"ktor-server-config-yaml"
        +"ktor-server-host-common"
        +"ktor-server-jetty" including {
            +"ktor-server-jetty-test-http2"
        }
        +"ktor-server-jetty-jakarta" including {
            +"ktor-server-jetty-test-http2-jakarta"
        }
        +"ktor-server-servlet"
        +"ktor-server-servlet-jakarta"
        +"ktor-server-tomcat"
        +"ktor-server-tomcat-jakarta"
        +"ktor-server-netty"
        +"ktor-server-cio"

        +"ktor-server-test-host"
        +"ktor-server-test-base"
        +"ktor-server-test-suites"
        +"ktor-server-tests"

        nested("ktor-server-plugins") {
            +"ktor-server-auth"
            +"ktor-server-auth-jwt"
            +"ktor-server-auth-ldap"
            +"ktor-server-auto-head-response"
            +"ktor-server-body-limit"
            +"ktor-server-caching-headers"
            +"ktor-server-call-id"
            +"ktor-server-call-logging"
            +"ktor-server-compression"
            +"ktor-server-conditional-headers"
            +"ktor-server-content-negotiation"
            +"ktor-server-cors"
            +"ktor-server-csrf"
            +"ktor-server-data-conversion"
            +"ktor-server-default-headers"
            +"ktor-server-di"
            +"ktor-server-double-receive"
            +"ktor-server-forwarded-header"
            +"ktor-server-freemarker"
            +"ktor-server-hsts"
            +"ktor-server-html-builder"
            +"ktor-server-htmx"
            +"ktor-server-http-redirect"
            +"ktor-server-i18n"
            +"ktor-server-jte"
            +"ktor-server-method-override"
            +"ktor-server-metrics"
            +"ktor-server-metrics-micrometer"
            +"ktor-server-mustache"
            +"ktor-server-openapi"
            +"ktor-server-partial-content"
            +"ktor-server-pebble"
            +"ktor-server-rate-limit"
            +"ktor-server-request-validation"
            +"ktor-server-resources"
            +"ktor-server-sessions"
            +"ktor-server-sse"
            +"ktor-server-status-pages"
            +"ktor-server-swagger"
            +"ktor-server-thymeleaf"
            +"ktor-server-velocity"
            +"ktor-server-webjars"
            +"ktor-server-websockets"
        }
    }

    client {
        +"ktor-client-core"
        +"ktor-client-apache"
        +"ktor-client-apache5"
        +"ktor-client-android"
        +"ktor-client-cio"
        +"ktor-client-curl"
        +"ktor-client-ios"
        +"ktor-client-darwin"
        +"ktor-client-darwin-legacy"
        +"ktor-client-winhttp"
        +"ktor-client-java"
        +"ktor-client-jetty"
        +"ktor-client-jetty-jakarta"
        +"ktor-client-js"
        +"ktor-client-mock"
        +"ktor-client-okhttp"

        +"ktor-client-test-base"
        +"ktor-client-tests"

        nested("ktor-client-plugins") {
            +"ktor-client-auth"
            +"ktor-client-bom-remover"
            +"ktor-client-call-id"
            +"ktor-client-content-negotiation" including {
                +"ktor-client-content-negotiation-tests"
            }
            +"ktor-client-encoding"
            +"ktor-client-json" including {
                +"ktor-client-gson"
                +"ktor-client-jackson"
                +"ktor-client-serialization"
            }
            +"ktor-client-logging"
            +"ktor-client-resources"
            +"ktor-client-websockets"
        }
    }

    shared {
        +"ktor-call-id"
        +"ktor-events"
        +"ktor-resources"
        +"ktor-serialization" including {
            +"ktor-serialization-kotlinx" including {
                +"ktor-serialization-kotlinx-json"
                +"ktor-serialization-kotlinx-cbor"
                +"ktor-serialization-kotlinx-xml"
                +"ktor-serialization-kotlinx-protobuf"
                +"ktor-serialization-kotlinx-tests"
            }
            +"ktor-serialization-gson"
            +"ktor-serialization-jackson"
            +"ktor-serialization-tests"
        }
        +"ktor-sse"
        +"ktor-htmx" including {
            +"ktor-htmx-html"
        }
        +"ktor-websocket-serialization"
        +"ktor-websockets"
        +"ktor-test-base"
    }

    +"ktor-network" including {
        +"ktor-network-tls" including {
            +"ktor-network-tls-certificates"
        }
    }

    +"ktor-http" including {
        +"ktor-http-cio"
    }

    +"ktor-io"
    +"ktor-utils"
    +"ktor-bom"
    +"ktor-test-dispatcher"
    +"ktor-java-modules-test"
    +"ktor-dokka"
    +"ktor-version-catalog"
}

// region Project hierarchy DSL
@DslMarker
annotation class ProjectDsl

@ProjectDsl
sealed class ProjectScope(private val settings: Settings) {

    /** Declares subproject in the current project scope. */
    operator fun String.unaryPlus(): ProjectScope {
        val projectName = this@unaryPlus
        val projectPath = subprojectPath(projectName)

        settings.include(projectName)
        settings.project(":$projectName").projectDir = settings.settingsDir.resolve(projectPath)
        return NestedProjectScope(settings, projectPath)
    }

    /**
     * Adds one more level of nesting to the projects declared in the [nested] lambda.
     * The group directory is not considered as a Gradle project.
     */
    fun nested(groupName: String, nested: ProjectScope.() -> Unit) {
        NestedProjectScope(settings, subprojectPath(groupName)).nested()
    }

    abstract fun subprojectPath(projectName: String): String
}

class RootProjectScope(settings: Settings) : ProjectScope(settings) {
    override fun subprojectPath(projectName: String) = projectName
}

class NestedProjectScope(settings: Settings, private val basePath: String) : ProjectScope(settings) {
    override fun subprojectPath(projectName: String): String = "$basePath/$projectName"
}

/**
 * Adds projects declared in the [nested] function to [this] project scope.
 * Should be used in combination with functions returning [ProjectScope].
 * For example:
 * ```
 * projects {
 *     +"foo" including {
 *         +"bar"
 *     }
 * }
 * ```
 */
infix fun ProjectScope.including(nested: ProjectScope.() -> Unit) = nested()

fun Settings.projects(nested: RootProjectScope.() -> Unit) = RootProjectScope(this).nested()

/** Declares projects related to server implementation. */
fun RootProjectScope.server(nested: ProjectScope.() -> Unit) = +"ktor-server" including nested
fun RootProjectScope.client(nested: ProjectScope.() -> Unit) = +"ktor-client" including nested
fun RootProjectScope.shared(nested: ProjectScope.() -> Unit) = nested("ktor-shared", nested)
// endregion

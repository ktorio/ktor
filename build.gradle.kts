/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.konan.target.*

buildscript {
    /*
     * These property group is used to build ktor against Kotlin compiler snapshot.
     * How does it work:
     * When build_snapshot_train is set to true, kotlin_version property is overridden with kotlin_snapshot_version,
     * atomicfu_version, coroutines_version, serialization_version and kotlinx_io_version are overwritten by TeamCity environment.
     * Additionally, mavenLocal and Sonatype snapshots are added to repository list and stress tests are disabled.
     * DO NOT change the name of these properties without adapting kotlinx.train build chain.
     */
    extra["build_snapshot_train"] = rootProject.properties["build_snapshot_train"].let { it != null && it != "" }
    val build_snapshot_train: Boolean by extra
    if (build_snapshot_train) {
        extra["kotlin_version"] = rootProject.properties["kotlin_snapshot_version"]
        val kotlin_version: String? by extra
        if (kotlin_version == null) {
            throw IllegalArgumentException(
                "'kotlin_snapshot_version' should be defined when building with snapshot compiler"
            )
        }
        repositories {
            mavenLocal()
            maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        }

        configurations.classpath {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(kotlin_version)
                }
            }
        }
    }
    // These three flags are enabled in train builds for JVM IR compiler testing
    extra["jvm_ir_enabled"] = rootProject.properties["enable_jvm_ir"] != null
    extra["jvm_ir_api_check_enabled"] = rootProject.properties["enable_jvm_ir_api_check"] != null
    // This flag is also used in settings.gradle to exclude native-only projects
    extra["native_targets_enabled"] = rootProject.properties["disable_native_targets"] == null

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    val kotlin_version: String by extra
    val dokka_version: String by extra
    val atomicfu_version: String by extra
    val validator_version: String by extra
    val android_gradle_version: String by extra
    val ktlint_version: String by extra

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokka_version")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("org.jetbrains.kotlinx:binary-compatibility-validator:$validator_version")
        classpath("com.android.tools.build:gradle:$android_gradle_version")
        classpath("org.jmailen.gradle:kotlinter-gradle:$ktlint_version")
    }

    CacheRedirector.configureBuildScript(rootProject, this)
}

val releaseVersion: String by extra
val native_targets_enabled: Boolean by extra

extra["configuredVersion"] = if (project.hasProperty("releaseVersion")) releaseVersion else project.version
extra["globalM2"] = "$buildDir/m2"
extra["publishLocal"] = project.hasProperty("publishLocal")

val configuredVersion: String by extra
val build_snapshot_train: Boolean by extra
val atomicfu_version: String by extra
val coroutines_version: String by extra
val serialization_version: String by extra

apply(from = "gradle/experimental.gradle")
apply(from = "gradle/verifier.gradle")

val experimentalAnnotations: List<String> by extra

/**
 * `darwin` is subset of `posix`.
 * Don't create `posix` and `darwin` sourceSets in single project.
 */
val platforms = mutableListOf("common", "jvm", "js")

if (native_targets_enabled) {
    platforms += listOf("posix", "darwin")
}

extra["skipPublish"] = mutableListOf<String>()
extra["nonDefaultProjectStructure"] = mutableListOf("ktor-bom")

fun projectNeedsPlatform(project: Project, platform: String): Boolean {
    val skipPublish: List<String> by rootProject.extra
    if (skipPublish.contains(project.name)) return platform == "jvm"

    val files = project.projectDir.listFiles() ?: emptyArray()
    val hasPosix = files.any { it.name == "posix" }
    val hasDarwin = files.any { it.name == "darwin" }

    if (hasPosix && hasDarwin) return false

    if (hasPosix && platform == "darwin") return false
    if (hasDarwin && platform == "posix") return false
    if (!hasPosix && !hasDarwin && platform == "darwin") return false

    return files.any { it.name == "common" || it.name == platform }
}

fun check(version: Any, libVersion: String, libName: String) {
    if (version != libVersion) {
        error("Current deploy version is $version, but $libName version is not overridden ($libVersion)")
    }
}

val disabledExplicitApiModeProjects = listOf(
    "ktor-client-tests",
    "ktor-client-json-tests",
    "ktor-server-test-host",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-client-content-negotiation-tests",
)

apply(from = "gradle/compatibility.gradle")

allprojects {
    group = "io.ktor"
    version = configuredVersion
    extra["hostManager"] = HostManager()

    if (build_snapshot_train) {
        extra["kotlin_version"] = rootProject.properties["kotlin_snapshot_version"]
        var kotlin_version: String by extra
        println("Using Kotlin $kotlin_version for project $this")
        val deployVersion = properties["DeployVersion"]
        if (deployVersion != null) version = deployVersion

        val skipSnapshotChecks = rootProject.properties["skip_snapshot_checks"] != null
        if (!skipSnapshotChecks) {
            check(version, atomicfu_version, "atomicfu")
            check(version, coroutines_version, "coroutines")
            check(version, serialization_version, "serialization")
        }
        kotlin_version = extra["kotlin_snapshot_version"] as String
        repositories {
            mavenLocal()
            maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    }

    CacheRedirector.configure(this)

    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (nonDefaultProjectStructure.contains(project.name)) return@allprojects

    apply(plugin = "kotlin-multiplatform")

    apply(from = rootProject.file("gradle/utility.gradle"))

    extra["nativeTargets"] = mutableListOf<String>()
    extra["nativeCompilations"] = mutableListOf<String>()

    platforms.forEach { platform ->
        if (projectNeedsPlatform(this, platform)) {
            apply(from = rootProject.file("gradle/$platform.gradle"))
        }
    }

    configurations {
        maybeCreate("testOutput")
    }

    kotlin {
        if (!disabledExplicitApiModeProjects.contains(project.name)) {
            explicitApi()
        }

        sourceSets.matching { !(it.name in listOf("main", "test")) }.all {
            val srcDir = if (name.endsWith("Main")) "src" else "test"
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
            val platform = name.dropLast(4)

            kotlin.srcDir("$platform/$srcDir")
            resources.srcDir("$platform/${resourcesPrefix}resources")

            languageSettings.apply {
                progressiveMode = true
                experimentalAnnotations.forEach { useExperimentalAnnotation(it) }

                if (project.path.startsWith(":ktor-server:ktor-server") && project.name != "ktor-server-core") {
                    useExperimentalAnnotation("io.ktor.server.engine.EngineAPI")
                }
            }
        }
    }

    val skipPublish: List<String> by rootProject.extra
    if (!skipPublish.contains(project.name)) {
        apply(from = rootProject.file("gradle/dokka.gradle"))
        apply(from = rootProject.file("gradle/publish.gradle"))
    }
}

if (project.hasProperty("enableCodeStyle")) {
    apply(from = "gradle/codestyle.gradle")
}

println("Using Kotlin compiler version: ${org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION}")
if (build_snapshot_train) {
    println("Hacking test tasks, removing stress and flaky tests")
    allprojects {
        tasks.withType<Test>().all {
            exclude("**/*ServerSocketTest*")
            exclude("**/*NettyStressTest*")
            exclude("**/*CIOMultithreadedTest*")
            exclude("**/*testBlockingConcurrency*")
            exclude("**/*testBigFile*")
            exclude("**/*numberTest*")
            exclude("**/*testWithPause*")
            exclude("**/*WebSocketTest*")
            exclude("**/*PostTest*")
            exclude("**/*testCustomUrls*")
            exclude("**/*testStaticServeFromDir*")
            exclude("**/*testRedirect*")
            exclude("**/*CIOHttpsTest*")
        }
    }

    println("Manifest of kotlin-compiler-embeddable.jar")

    subprojects.filter { it.name == "ktor-client" }.forEach {
        configurations.matching { it.name == "kotlinCompilerClasspath" }.all {
            resolvedConfiguration.files.filter { it.name.contains("kotlin-compiler-embeddable") }.forEach {
                val manifest = zipTree(it).matching {
                    include("META-INF/MANIFEST.MF")
                }.files.first()

                manifest.readLines().forEach {
                    println(it)
                }
            }
        }
    }
}

afterEvaluate {
    val allCompileKotlinTasks = subprojects.mapNotNull {
        if (it.hasProperty("compileKotlinJvm")) {
            it.property("compileKotlinJvm")
        } else null
    }

    tasks.create<org.jetbrains.dokka.gradle.DokkaTask>("dokkaWebsite") {
        outputFormat = "kotlin-website"
        outputDirectory = "${rootProject.projectDir}/apidoc"

        kotlinTasks(closureOf<Any?> { allCompileKotlinTasks })

        reportUndocumented = false
    }
}

if (project.hasProperty("enable-coverage")) {
    apply(from = "gradle/jacoco.gradle")
}

// Disable binary compatibility check for JVM IR compiler output by default
val jvm_ir_enabled: Boolean by extra
val jvm_ir_api_check_enabled: Boolean by extra

if (jvm_ir_enabled) {
    subprojects {
        afterEvaluate {
            configure(tasks.matching { it.name == "apiCheck" }) {
                enabled = enabled && jvm_ir_api_check_enabled
            }
        }
    }
}

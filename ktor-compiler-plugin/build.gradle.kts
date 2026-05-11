/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.base")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
    id("org.jetbrains.kotlin.plugin.serialization")
    `java-test-fixtures`
    idea
    id("ktorbuild.publish")
}

description = "Ktor Compiler Plugin"

val testSamples by configurations.creating
val testData by sourceSets.creating {
    java.setSrcDirs(listOf("testData"))
    compileClasspath += testSamples
    runtimeClasspath += testSamples
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test {
        java.setSrcDirs(listOf("test", "test-gen"))
    }
}

idea.module.generatedSourceDirs.add(projectDir.resolve("test-gen"))

dependencies {
    compileOnly(libs.kotlin.compilerEmbeddable)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.compilerTestFramework)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesApi(libs.kotlinx.serialization.compiler.embedded)

    testSamples(projects.ktorServerCore)
    testSamples(projects.ktorServerCio)
    testSamples(projects.ktorServerContentNegotiation)
    testSamples(projects.ktorServerAuth)
    testSamples(projects.ktorServerAuthJwt)
    testSamples(projects.ktorServerResources)
    testSamples(projects.ktorServerRoutingOpenapi)
    testSamples(projects.ktorServerTestHost)
    testSamples(projects.ktorClientCore)
    testSamples(projects.ktorClientApache)
    testSamples(projects.ktorSerializationKotlinxJson)
    testSamples(projects.ktorOpenapiSchema)
    testSamples(libs.kotlin.test)

    testRuntimeOnly(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.kotlin.test)
    testRuntimeOnly(libs.kotlin.reflect)
    testRuntimeOnly(libs.kotlin.script.runtime)
    testRuntimeOnly(libs.kotlin.annotations.jvm)
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(rootProject.group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}.${project.name}\"")
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

val testSamplesClasspath: FileCollection = testSamples
val testDataDir: File = layout.projectDirectory.dir("testData").asFile
val rootDirFile: File = rootDir
val testRuntimeClasspath: FileCollection = configurations.testRuntimeClasspath.get()

tasks {
    val configureCompilerPluginTest: Test.() -> Unit = {
        dependsOn(testSamples)
        useJUnitPlatform()
        workingDir = rootDirFile
        maxHeapSize = "1g"

        // Resolve classpath lazily to avoid forcing project evaluation during configuration
        jvmArgumentProviders.add(
            CompilerPluginTestArgumentProvider(
                testSamples = testSamplesClasspath,
                testRuntimeClasspath = testRuntimeClasspath,
                testDataDir = testDataDir,
            ),
        )

        systemProperty("idea.ignore.disabled.plugins", "true")
        systemProperty("idea.home.path", rootDirFile)
    }

    test(configureCompilerPluginTest)

    val updateSnapshots by registering(Test::class) {
        group = "verification"
        configureCompilerPluginTest()
        systemProperty("testSamples.replaceSnapshots", "true")
    }

    val generateTests by registering(JavaExec::class) {
        inputs
            .dir(layout.projectDirectory.dir("testData"))
            .withPropertyName("testData")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs
            .dir(layout.projectDirectory.dir("test-gen"))
            .withPropertyName("generatedTests")

        classpath = sourceSets.testFixtures.get().runtimeClasspath
        mainClass.set("io.ktor.compiler.GenerateTestsKt")
        workingDir = rootDir
    }

    compileTestKotlin {
        dependsOn(generateTests)
    }
}

class CompilerPluginTestArgumentProvider(
    private val testSamples: FileCollection,
    private val testRuntimeClasspath: FileCollection,
    private val testDataDir: File,
) : CommandLineArgumentProvider {

    private val frameworkLibraries = listOf(
        "org.jetbrains.kotlin.test.kotlin-stdlib" to "kotlin-stdlib",
        "org.jetbrains.kotlin.test.kotlin-stdlib-jdk8" to "kotlin-stdlib-jdk8",
        "org.jetbrains.kotlin.test.kotlin-reflect" to "kotlin-reflect",
        "org.jetbrains.kotlin.test.kotlin-test" to "kotlin-test",
        "org.jetbrains.kotlin.test.kotlin-script-runtime" to "kotlin-script-runtime",
        "org.jetbrains.kotlin.test.kotlin-annotations-jvm" to "kotlin-annotations-jvm",
    )

    override fun asArguments(): Iterable<String> = buildList {
        add("-DtestSamples.classpath=${testSamples.asPath}")
        add("-DtestSamples.location=${testDataDir.absolutePath}")

        val runtimeFiles = testRuntimeClasspath.files
        for ((propName, jarName) in frameworkLibraries) {
            val path = runtimeFiles
                .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
                ?.absolutePath
                ?: continue
            add("-D$propName=$path")
        }
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.targets.javaModuleName

plugins {
    id("ktorbuild.base")
    id("java-library")
}

description = "Internal module for checking JPMS compliance"

val jvmProjects = projectsWithTag(ProjectTag.Jvm)

val generateModuleInfo = tasks.register("generateModuleInfo") {
    val modules = jvmProjects.mapValue(Project::javaModuleName)
    inputs.property("modules", modules)

    val moduleInfoFile = layout.projectDirectory.file("src/main/java/module-info.java")
    outputs.file(moduleInfoFile)

    doLast {
        moduleInfoFile.asFile
            .apply {
                parentFile.mkdirs()
                createNewFile()
            }
            .bufferedWriter().use { writer ->
                writer.write("module io.ktor.test.module {\n")
                modules.get().forEach { writer.write("\trequires $it;\n") }
                writer.write("}")
            }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateModuleInfo)

    val emptyClasspath = objects.fileCollection()
    doFirst {
        options.compilerArgs.addAll(listOf("--module-path", classpath.asPath))
        classpath = emptyClasspath
    }
}

// Here should be specified the latest LTS version
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations.implementation {
    dependencies.addAllLater(jvmProjects.mapValue(project.dependencies::create))
}

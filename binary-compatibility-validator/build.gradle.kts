/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.plugin.*

val binaryCompatibility = configurations.create("binaryCompatibility").apply {
    usesPlatformOf(kotlin.jvm())
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:1.3.21")
            api("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.0.5")
            api("org.ow2.asm:asm:6.0")
            api("org.ow2.asm:asm-tree:6.0")
        }
    }

    val jvmTest by getting {
        dependencies {
            api("org.jetbrains.kotlin:kotlin-test-junit:1.3.21")
            api("junit:junit:4.12")
        }
    }
}

apply(from = rootProject.file("gradle/compatibility.gradle"))

tasks.getByName<Test>("jvmTest") {
    dependsOn(binaryCompatibility)

    val projectDependencies = binaryCompatibility.allDependencies.withType<ProjectDependency>()
    val artifacts = projectDependencies.joinToString(File.pathSeparator) { it.dependencyProject.name }
    val modules = projectDependencies.joinToString(File.pathSeparator) { it.dependencyProject.projectDir.path }

    systemProperty("validator.input.artifactNames", artifacts)
    systemProperty("validator.input.modules", modules)
    systemProperty("overwrite.output", project.properties["overwrite.output"].toString())
    jvmArgs("-ea")
}

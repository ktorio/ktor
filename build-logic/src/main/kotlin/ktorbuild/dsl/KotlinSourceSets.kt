/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinSourceSetConvention
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

private typealias KotlinSourceSets = NamedDomainObjectContainer<KotlinSourceSet>
private typealias KotlinSourceSetProvider = NamedDomainObjectProvider<KotlinSourceSet>
private typealias OptionalKotlinSourceSetProvider = NamedDomainObjectSet<KotlinSourceSet>

// Additional accessors to the ones declared in KotlinMultiplatformSourceSetConventions

val KotlinSourceSets.posixMain: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.darwinMain: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.darwinTest: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.desktopMain: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.desktopTest: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.windowsMain: KotlinSourceSetProvider by KotlinSourceSetConvention
val KotlinSourceSets.windowsTest: KotlinSourceSetProvider by KotlinSourceSetConvention

val KotlinSourceSets.optional: OptionalSourceSets get() = OptionalSourceSets(this)

@JvmInline
value class OptionalSourceSets(private val sourceSets: KotlinSourceSets) {
    val androidMain: OptionalKotlinSourceSetProvider get() = optional("androidMain")
    val androidTest: OptionalKotlinSourceSetProvider get() = optional("androidTest")
    val androidDeviceTest: OptionalKotlinSourceSetProvider get() = optional("androidDeviceTest")

    private fun optional(name: String): OptionalKotlinSourceSetProvider = sourceSets.named { it == name }
}

fun OptionalKotlinSourceSetProvider.dependencies(handler: KotlinDependencyHandler.() -> Unit) {
    configureEach { dependencies(handler) }
}

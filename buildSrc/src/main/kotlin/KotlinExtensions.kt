/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*

fun Project.kotlin(block: KotlinMultiplatformExtension.() -> Unit) {
    configure(block)
}

val Project.kotlin: KotlinMultiplatformExtension get() = the()

val NamedDomainObjectContainer<KotlinSourceSet>.jvmMain: NamedDomainObjectProvider<KotlinSourceSet>
    get() = named<KotlinSourceSet>("jvmMain")

val NamedDomainObjectContainer<KotlinSourceSet>.jvmTest: NamedDomainObjectProvider<KotlinSourceSet>
    get() = named<KotlinSourceSet>("jvmTest")

val NamedDomainObjectContainer<KotlinSourceSet>.commonMain: NamedDomainObjectProvider<KotlinSourceSet>
    get() = named<KotlinSourceSet>("commonMain")

val NamedDomainObjectContainer<KotlinSourceSet>.commonTest: NamedDomainObjectProvider<KotlinSourceSet>
    get() = named<KotlinSourceSet>("commonTest")

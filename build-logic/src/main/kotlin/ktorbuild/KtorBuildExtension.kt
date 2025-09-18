/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.targets.KtorTargets
import org.gradle.api.JavaVersion
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class KtorBuildExtension private constructor(
    providers: ProviderFactory,
    val targets: KtorTargets,
) {

    @Inject
    constructor(
        objects: ObjectFactory,
        providers: ProviderFactory,
    ) : this(providers, targets = objects.newInstance())

    private val buildingOnTeamCity: Provider<Boolean> =
        providers.environmentVariable("TEAMCITY_VERSION").map(String::isNotBlank)

    val isCI: Provider<Boolean> =
        providers.environmentVariable("CI")
            .map(String::isNotBlank)
            .orElse(buildingOnTeamCity)
            .orElse(false)

    /**
     * The JDK version to be used for testing.
     *
     * The value is determined from the Gradle property "test.jdk".
     * If the property is not specified, it defaults to the current JDK used by Gradle.
     *
     * For example, to run tests against JDK 8, run a test task with flag "-Ptest.jdk=8"
     * or put this property to `gradle.properties`.
     */
    val jvmTestToolchain: Provider<JavaLanguageVersion> =
        providers.gradleProperty("test.jdk")
            .orElse(providers.provider { JavaVersion.current().majorVersion })
            .map(JavaLanguageVersion::of)

    /** Host operating system. */
    val os: Provider<OperatingSystem> = providers.provider { OperatingSystem.current() }

    companion object {
        const val NAME = "ktorBuild"

        /** The default (minimal) JDK version used for building the project. */
        const val DEFAULT_JDK = 8
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

package ktorbuild.targets

import ktorbuild.internal.KotlinHierarchyTracker
import ktorbuild.internal.TrackedKotlinHierarchyTemplate
import ktorbuild.internal.gradle.ProjectGradleProperties
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Problems
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import javax.inject.Inject

/**
 * Represents configuration for Kotlin Multiplatform targets in a Ktor project, enabling
 * or disabling specific targets based on the project structure or Gradle properties.
 *
 * By default, a target is enabled if its source set directory exists in the project,
 * unless explicitly disabled in properties.
 *
 * Targets can be enabled or disabled using `gradle.properties` file with properties starting with `target.` prefix:
 * ```properties
 * # Enable/disable specific target
 * target.jvm=true
 * target.watchosDeviceArm64=false
 *
 * # Enable/disable group of targets
 * target.posix=true
 * target.androidNative=false
 * ```
 *
 * There are two sub-targets available for targets `js` and `wasmJs`: `browser` and `nodeJs`.
 * Sub-targets inherit their parent target's state but can be individually configured using properties.
 * ```properties
 * # Disable specific sub-targets
 * target.js.browser=false
 * target.wasmJs.browser=false
 * ```
 *
 * See the full list of targets and target groups in [KtorTargets.hierarchyTemplate].
 */
abstract class KtorTargets internal constructor(
    private val layout: ProjectLayout,
    properties: ProjectGradleProperties,
) {

    @Inject
    internal constructor(
        layout: ProjectLayout,
        objects: ObjectFactory,
    ) : this(layout, properties = objects.newInstance())

    private val targetStates: MutableMap<String, Boolean> by lazy { loadDefaults(properties) }

    private val directories: Set<String> by lazy {
        layout.projectDirectory.asFile.walk()
            .maxDepth(1)
            .filter { it.isDirectory }
            .map { it.name }
            .toSet()
    }

    val hasJvm: Boolean get() = isEnabled("jvm")
    val hasJs: Boolean get() = isEnabled("js")
    val hasWasmJs: Boolean get() = isEnabled("wasmJs")

    val hasJsOrWasmJs: Boolean get() = hasJs || hasWasmJs
    val hasNative: Boolean get() = resolveTargets("posix").any(::isEnabled)
    val hasLinux: Boolean get() = resolveTargets("linux").any(::isEnabled)
    val hasWindows: Boolean get() = resolveTargets("windows").any(::isEnabled)
    val hasDarwin: Boolean get() = resolveTargets("darwin").any(::isEnabled)
    val hasAndroidNative: Boolean get() = resolveTargets("androidNative").any(::isEnabled)

    /**
     * Determines if the specified [target] is enabled.
     *
     * The target is considered enabled if:
     * - It wasn't explicitly disabled in `gradle.properties`, and
     *   - The project has at least one source set used by this target, or
     *   - The target is explicitly enabled in `gradle.properties`
     *
     * For sub-targets (e.g., 'js.browser'), the state is inherited from the parent target
     * unless explicitly configured in `gradle.properties`.
     */
    fun isEnabled(target: String): Boolean = targetStates.getOrPut(target) {
        // Sub-targets inherit parent state
        if (target.contains(".")) {
            isEnabled(target.substringBefore("."))
        } else {
            hierarchyTracker.targetSourceSets.getValue(target).any { it in directories }
        }
    }

    private fun loadDefaults(properties: ProjectGradleProperties): MutableMap<String, Boolean> {
        val defaults = mutableMapOf<String, Boolean>()
        for ((key, rawValue) in properties.byNamePrefix("target.")) {
            val value = rawValue.toBoolean()
            for (target in resolveTargets(key)) defaults[target] = value
        }
        return defaults
    }

    companion object {
        private val hierarchyTracker = KotlinHierarchyTracker()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        internal val hierarchyTemplate = TrackedKotlinHierarchyTemplate(hierarchyTracker) {
            withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

            common {
                group("posix") {
                    group("windows") { withMingw() }

                    group("nix") {
                        group("linux") { withLinux() }

                        group("darwin") {
                            group("ios") { withIos() }
                            group("tvos") { withTvos() }
                            group("watchos") { withWatchos() }
                            group("macos") { withMacos() }
                        }

                        group("androidNative") {
                            group("androidNative64") {
                                withAndroidNativeX64()
                                withAndroidNativeArm64()
                            }

                            group("androidNative32") {
                                withAndroidNativeX86()
                                withAndroidNativeArm32()
                            }
                        }
                    }
                }

                group("jsAndWasmShared") {
                    withJs()
                    withWasmJs()
                }

                group("jvmAndPosix") {
                    withJvm()
                    group("posix")
                }

                group("desktop") {
                    group("linux")
                    group("windows")
                    group("macos")
                }

                group("nonJvm") {
                    group("posix")
                    group("jsAndWasmShared")
                }

                group("nonDarwinPosix") {
                    group("windows")
                    group("linux")
                    group("androidNative")
                }
            }
        }

        /** Returns targets corresponding to the provided [sourceSet]. */
        fun resolveTargets(sourceSet: String): Set<String> = hierarchyTracker.groups[sourceSet] ?: setOf(sourceSet)
    }
}

internal fun KotlinMultiplatformExtension.addTargets(targets: KtorTargets) {
    if (targets.hasJvm) jvm()

    if (targets.hasJs) js { addSubTargets(targets) }
    @OptIn(ExperimentalWasmDsl::class)
    if (targets.hasWasmJs) wasmJs { addSubTargets(targets) }

    // Native targets
    // See: https://kotlinlang.org/docs/native-target-support.html

    // Tier 1
    if (targets.isEnabled("macosX64")) macosX64()
    if (targets.isEnabled("macosArm64")) macosArm64()
    if (targets.isEnabled("iosArm64")) iosArm64()
    if (targets.isEnabled("iosX64")) iosX64()
    if (targets.isEnabled("iosSimulatorArm64")) iosSimulatorArm64()

    // Tier 2
    if (targets.isEnabled("linuxArm64")) linuxArm64()
    if (targets.isEnabled("linuxX64")) linuxX64()
    if (targets.isEnabled("watchosArm32")) watchosArm32()
    if (targets.isEnabled("watchosArm64")) watchosArm64()
    if (targets.isEnabled("watchosX64")) watchosX64()
    if (targets.isEnabled("watchosSimulatorArm64")) watchosSimulatorArm64()
    if (targets.isEnabled("tvosArm64")) tvosArm64()
    if (targets.isEnabled("tvosX64")) tvosX64()
    if (targets.isEnabled("tvosSimulatorArm64")) tvosSimulatorArm64()

    // Tier 3
    if (targets.isEnabled("androidNativeArm32")) androidNativeArm32()
    if (targets.isEnabled("androidNativeArm64")) androidNativeArm64()
    if (targets.isEnabled("androidNativeX86")) androidNativeX86()
    if (targets.isEnabled("androidNativeX64")) androidNativeX64()
    if (targets.isEnabled("mingwX64")) mingwX64()
    if (targets.isEnabled("watchosDeviceArm64")) watchosDeviceArm64()

    freezeSourceSets()
    flattenSourceSetsStructure()
}

private const val IGNORE_EXTRA_SOURCE_SETS_PROPERTY = "ktor.ignoreExtraSourceSets"

private val ktorProblemGroup = ProblemGroup.create("ktor", "Ktor")
private val extraSourceSetProblemId = ProblemId.create(
    "ktor-extra-source-sets",
    "Extra source sets detected",
    ktorProblemGroup,
)

/**
 * Ensures that no additional source sets have been added after the initial automatic configuration phase.
 *
 * By default, it throws an [IllegalStateException] if extra source sets are detected.
 * When [IGNORE_EXTRA_SOURCE_SETS_PROPERTY] property is set to `true`, extra source sets are ignored instead.
 */
private fun KotlinMultiplatformExtension.freezeSourceSets() {
    val problemReporter = project.serviceOf<Problems>().reporter
    val ignoreExtraSourceSets = project.providers.gradleProperty(IGNORE_EXTRA_SOURCE_SETS_PROPERTY).orNull.toBoolean()
    val extraSourceSets = mutableSetOf<KotlinSourceSet>()

    sourceSets.whenObjectAdded { extraSourceSets.add(this) }

    project.afterEvaluate {
        if (extraSourceSets.isNotEmpty()) {
            val names = extraSourceSets.map { it.name }.sorted()
            val context = "Detected extra source sets registered manually: $names (project ${project.path})"

            if (ignoreExtraSourceSets) {
                problemReporter.reportExtraSourceSetsWarning(context)
                sourceSets.removeAll(extraSourceSets)
            } else {
                throw problemReporter.reportExtraSourceSetsError(context)
            }
        }
    }
}

private fun ProblemReporter.reportExtraSourceSetsWarning(context: String) = report(extraSourceSetProblemId) {
    contextualLabel(context)
    details("Ignoring them because '$IGNORE_EXTRA_SOURCE_SETS_PROPERTY' property is set to 'true'.")
}

private fun ProblemReporter.reportExtraSourceSetsError(context: String) = throwing(
    IllegalStateException(extraSourceSetProblemId.displayName),
    extraSourceSetProblemId,
) {
    contextualLabel(context)
    details(
        """
        Ktor automatically registers source sets depending on the present source directories and enabled targets,
        so manual registration of extra source sets is not allowed. 

        Possible reasons:
        1. The source sets were registered explicitly using 'kotlin' extension.
        2. Dependencies for the source sets were declared.
           Declaring dependencies for a source set implicitly creates it.
        """.trimIndent()
    )
    solution(
        """
        Remove manual source set registration.
        Add source directory or enable the target using 'target.[name]=true' property to register
        the source set automatically.
        """.trimIndent()
    )
    solution(
        """
        The source sets were disabled using 'target.[name]=false' property, but dependencies for them are still declared.
        If this is intentional, specify '$IGNORE_EXTRA_SOURCE_SETS_PROPERTY=true' property to suppress this error.
        """.trimIndent()
    )
}

/**
 * Changes the source sets structure to a more concise format.
 *
 * Transforms from the default Kotlin Multiplatform structure:
 * ```
 * <projectDir>
 * - src/
 *   - commonMain/
 *     - kotlin/
 *   - jvmMain/
 *     - kotlin/
 *     - resources/
 *   - jvmTest/
 *     - kotlin/
 *     - resources/
 * ```
 *
 * To a flattened platform-centric structure:
 * ```
 * <projectDir>
 * - common/
 *   - src/             # commonMain kotlin sources
 * - jvm/
 *   - src/             # jvmMain kotlin sources
 *   - resources/       # jvmMain resources
 *   - test/            # jvmTest kotlin sources
 *   - test-resources/  # jvmTest resources
 * ```
 */
private fun KotlinMultiplatformExtension.flattenSourceSetsStructure() {
    sourceSets
        .matching { it.name !in listOf("main", "test") }
        .all {
            val srcDir = if (name.endsWith("Main")) "src" else "test"
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
            val platform = name.dropLast(4)

            kotlin.setSrcDirs(listOf("$platform/$srcDir"))
            resources.setSrcDirs(listOf("$platform/${resourcesPrefix}resources"))
        }
}

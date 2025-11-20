/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.vcpkg

import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.targets.native.internal.KotlinNativeDownloadTask
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.util.DependencyDirectories
import java.io.File

fun Project.registerVcpkgInstallTask(
    library: String,
    target: String,
    configure: VcpkgInstall.() -> Unit = {}
): TaskProvider<VcpkgInstall> {
    val toolchainDir = getToolchainDirName(target)
    val downloadKotlinToolchain = if (toolchainDir != null) {
        tasks.named<KotlinNativeDownloadTask>("downloadKotlinNativeDistribution")
    } else {
        null
    }
    if (target == "linuxArm64") disableKotlinNativeDownloading()

    return tasks.register<VcpkgInstall>("${library}Install") {
        if (downloadKotlinToolchain != null && toolchainDir != null) {
            dependsOn(downloadKotlinToolchain)
            toolchainDirectory.set(DependencyDirectories.getDependenciesRoot().resolve(toolchainDir))
        }
        install(library, target)
        configure()
    }
}

// See: https://github.com/JetBrains/kotlin/blob/v2.2.21/kotlin-native/konan/konan.properties#L68-L71
private fun getToolchainDirName(target: String): String? = when (target) {
    "linuxX64" -> "x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"
    "linuxArm64" -> "aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2"
    else -> null
}

/**
 * Workaround for KT-36871.
 * It works because we don't need to download Kotlin Native.
 * We just need to unblock the Kotlin Gradle Plugin and let it download the toolchain.
 */
private fun Project.disableKotlinNativeDownloading() {
    val platform = HostManager.platformName()
    // See KotlinNativeDownloadTask.processToolchain implementation
    // We create a marker file to skip resolving Kotlin Native configuration
    val marker = DependencyDirectories.localKonanDir
        .resolve("kotlin-native-prebuilt-$platform-${getKotlinPluginVersion()}")
        .resolve("provisioned.ok") // NativeVersionValueSource.Companion.MARKER_FILE

    // This should be done before configuring KotlinNativeDownloadTask and calling KotlinNativeDownloadTask.obtain
    if (!marker.exists()) {
        println("KT-36871 Creating marker file to skip downloading kotlin-native-prebuilt:\n  $marker")
        marker.parentFile.mkdirs()
        marker.createNewFile()
    }

    project.configurations.named { it == "kotlinNativeBundleConfiguration" }.configureEach {
        resolutionStrategy.eachDependency {
            artifactSelection {
                val selector = requestedSelectors.single()
                selectArtifact(selector.type, selector.extension, "linux-x86_64")
            }
        }
    }
}

fun Project.registerSyncHeadersTask(
    taskName: String,
    from: TaskProvider<VcpkgInstall>,
    into: File,
    library: String,
): TaskProvider<Sync> = tasks.register<Sync>(taskName) {
    from(from.map { it.outputDir.get().dir("include/$library") })
    into(into.resolve(library))
}

fun Project.registerSyncBinariesTask(
    taskName: String,
    from: TaskProvider<VcpkgInstall>,
    into: File,
    configure: Sync.() -> Unit = {}
): TaskProvider<Sync> = tasks.register<Sync>(taskName) {
    from(from.map { it.outputDir.get().dir("lib") }) {
        exclude("pkgconfig")
    }
    into(into)
    configure()
}

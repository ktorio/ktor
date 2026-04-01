/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.vcpkg

import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.targets.native.internal.KotlinNativeDownloadTask
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

fun Project.registerVcpkgInstallTask(
    library: String,
    target: KonanTarget,
    configure: VcpkgInstall.() -> Unit = {}
): TaskProvider<VcpkgInstall> {
    val downloadKotlinNative = tasks.named<KotlinNativeDownloadTask>("downloadKotlinNativeDistribution")
    return tasks.register<VcpkgInstall>("${library}Install") {
        install(library, target)
        // Do not configure Toolchain on macOS as Konan uses native tools there
        if (!target.family.isAppleFamily) {
            nativeDirectoryLocation.set(downloadKotlinNative.flatMap { it.nativeDirectoryLocation })
        }
        configure()
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

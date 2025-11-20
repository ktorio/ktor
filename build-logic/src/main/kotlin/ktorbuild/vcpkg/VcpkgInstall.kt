/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.vcpkg

import ktorbuild.internal.withLimitedParallelism
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.util.DependencyDirectories
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "We rely on vcpkg's caches")
abstract class VcpkgInstall @Inject constructor(
    private val execOps: ExecOperations,
    layout: ProjectLayout,
    objects: ObjectFactory,
) : DefaultTask() {

    @get:InputDirectory
    internal abstract val manifestDir: DirectoryProperty

    @get:Input
    @get:Optional
    protected abstract val toolchainDirectory: Property<File>

    @get:Input
    internal abstract val triplet: Property<String>

    @get:Input
    abstract val useOverlays: Property<Boolean>

    private val installDir: Provider<Directory> = layout.buildDirectory.dir("vcpkg")
    private val outputDirProperty: DirectoryProperty = objects.directoryProperty()

    @get:OutputDirectory
    val outputDir: Provider<Directory>
        get() = outputDirProperty

    init {
        withLimitedParallelism("vcpkg", maxParallelTasks = 1)
    }

    /**
     * Run "vcpkg install" for the specified manifest directory with the configured [target].
     *
     * The directory should contain a vcpkg.json manifest file.
     */
    fun install(manifestPath: String, target: String) {
        val triplet = targetToTriplet(target)
        this.manifestDir.set(project.file(manifestPath))
        this.triplet.set(triplet)
        outputDirProperty.set(installDir.map { it.dir(triplet) })

        val toolchainDirectoryName = getToolchainDirName(target)
        if (toolchainDirectoryName != null) {
            toolchainDirectory.set(DependencyDirectories.defaultDependenciesRoot.resolve(toolchainDirectoryName))
        }
    }

    private fun targetToTriplet(target: String): String = when (target) {
        "macosArm64" -> "arm64-osx"
        "macosX64" -> "x64-osx"
        "linuxX64" -> "x64-linux"
        "linuxArm64" -> "arm64-linux"
        "mingwX64" -> "x64-mingw-static"
        else -> error("Unsupported target: $target")
    }

    @TaskAction
    internal fun run() {
        val vcpkgPath = if (HostManager.hostIsMingw) {
            "vcpkg"
        } else {
            checkNotNull(execOps.which("vcpkg")) { "Vcpkg is not installed" }
        }

        val installDir = installDir.get().asFile
        val manifestDir = manifestDir.get().asFile
        val overlayPortsDir = manifestDir.resolve("overlays")
        val overlayTripletsDir = manifestDir.resolve("triplets")
        val currentTriplet = triplet.get()

        val commandLine = listOfNotNull(
            vcpkgPath,
            "install",
            "--triplet=$currentTriplet",
            "--x-install-root=$installDir",
            "--x-manifest-root=$manifestDir",
            if (overlayPortsDir.isDirectory && useOverlays.get()) "--overlay-ports=$overlayPortsDir" else null,
            if (overlayTripletsDir.isDirectory) "--overlay-triplets=$overlayTripletsDir" else null
        )

        execOps.exec {
            commandLine(commandLine)
            if (toolchainDirectory.isPresent) setToolchainDir(toolchainDirectory.get())
        }
    }

    private fun ExecSpec.setToolchainDir(toolchainDirectory: File) {
        check(toolchainDirectory.exists()) {
            """
            Toolchain directory does not exist: $toolchainDirectory
            Run `./gradlew downloadKotlinNativeDistribution` to download toolchain.
            """.trimIndent()
        }
        environment("TOOLCHAIN_DIR", toolchainDirectory.absolutePath)
        environment("TOOLCHAIN_TARGET", toolchainDirectory.name.substringBefore("-gcc"))
    }
}

// See: https://github.com/JetBrains/kotlin/blob/v2.2.21/kotlin-native/konan/konan.properties#L68-L71
private fun getToolchainDirName(target: String): String? = when (target) {
    "linuxX64" -> "x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"
    "linuxArm64" -> "aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2"
    "mingwX64" -> "llvm-19-x86_64-windows-essentials-134"
    else -> null
}

private fun ExecOperations.which(command: String): String? {
    val whichCommandOutputStream = ByteArrayOutputStream()
    val result = exec {
        commandLine("which", command)
        standardOutput = whichCommandOutputStream
        isIgnoreExitValue = true
    }

    return if (result.exitValue == 0) whichCommandOutputStream.toString().trim() else null
}

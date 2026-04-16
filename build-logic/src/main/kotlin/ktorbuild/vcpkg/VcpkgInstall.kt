/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.vcpkg

import ktorbuild.internal.gradle.loadProperties
import ktorbuild.internal.withLimitedParallelism
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.konan.target.*
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
    internal abstract val target: Property<KonanTarget>

    @get:Input
    internal abstract val triplet: Property<String>

    @get:Input
    abstract val overlayPorts: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    internal abstract val nativeDirectoryLocation: RegularFileProperty

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
    fun install(manifestPath: String, target: KonanTarget) {
        this.target.set(target)
        val triplet = target.triplet()
        this.manifestDir.set(project.file(manifestPath))
        this.triplet.set(triplet)
        outputDirProperty.set(installDir.map { it.dir(triplet) })
    }

    private fun KonanTarget.triplet(): String = when (this) {
        KonanTarget.MACOS_ARM64 -> "arm64-osx"
        KonanTarget.MACOS_X64 -> "x64-osx"
        KonanTarget.LINUX_ARM64 -> "arm64-linux"
        KonanTarget.LINUX_X64 -> "x64-linux"
        KonanTarget.MINGW_X64 -> "x64-mingw-static"
        else -> error("Unsupported target: $this")
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
        val currentTriplet = triplet.get()

        val commandLine = mutableListOf(
            vcpkgPath,
            "install",
            "--triplet=$currentTriplet",
            "--x-install-root=$installDir",
            "--x-manifest-root=$manifestDir",
            "--no-print-usage",
        )
        for (overlayPort in overlayPorts.get()) {
            val overlayPortDir = manifestDir.resolve(overlayPort)
            commandLine.add("--overlay-ports=$overlayPortDir")
        }

        val overlayTripletsDir = manifestDir.resolve("triplets")
        if (overlayTripletsDir.isDirectory) commandLine.add("--overlay-triplets=$overlayTripletsDir")

        execOps.exec {
            commandLine(commandLine)
            configureToolchain()
        }
    }

    private fun ExecSpec.configureToolchain() {
        val nativeDirectoryLocationFile = nativeDirectoryLocation.orNull ?: return
        val nativeDirectoryLocation = nativeDirectoryLocationFile.asFile.readText().trim()
        val konanProperties = File(nativeDirectoryLocation, "konan/konan.properties").loadProperties()
        val configurables = loadConfigurables(
            target.get(),
            konanProperties,
            DependencyDirectories.defaultDependenciesRoot.absolutePath,
        )
        val gccConfigurables = configurables as? GccConfigurables

        val llvmHome = configurables.absoluteLlvmHome
        val sysroot = configurables.absoluteTargetSysRoot
        val triple = configurables.targetTriple.toString()
        val linker = configurables.absoluteLinkerOrNull
        val gccToolchain = gccConfigurables?.absoluteGccToolchain
        val libGcc = gccConfigurables?.libGcc?.let { File(sysroot).resolve(it).canonicalPath }

        logger.info(
            "Using toolchain: " +
                "llvmHome=$llvmHome, " +
                "sysroot=$sysroot, " +
                "triple=$triple, " +
                "linker=$linker, " +
                "gccToolchain=$gccToolchain, " +
                "libGcc=$libGcc"
        )
        environment("TOOLCHAIN_LLVM_HOME", llvmHome)
        environment("TOOLCHAIN_SYSROOT", sysroot)
        environment("TOOLCHAIN_TRIPLE", triple)
        linker?.let { environment("TOOLCHAIN_LINKER", it) }
        gccToolchain?.let { environment("TOOLCHAIN_GCC_TOOLCHAIN", it) }
        libGcc?.let { environment("TOOLCHAIN_LIBGCC", it) }
    }
}

private val Configurables.absoluteLinkerOrNull: String?
    get() = when (this) {
        is GccConfigurables -> absoluteLinker
        is MingwConfigurables -> absoluteLinker
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

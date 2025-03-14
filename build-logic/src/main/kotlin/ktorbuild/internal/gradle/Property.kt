/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

internal fun <T> Property<T>.finalizedOnRead(): Property<T> = apply { finalizeValueOnRead() }

internal fun Project.directoryProvider(fileProvider: () -> File): Provider<Directory> =
    layout.dir(provider(fileProvider))

internal fun Project.regularFileProvider(fileProvider: () -> File): Provider<RegularFile> =
    layout.file(provider(fileProvider))

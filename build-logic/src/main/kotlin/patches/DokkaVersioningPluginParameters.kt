/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("PackageDirectoryMismatch", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "unused")

package org.jetbrains.dokka.gradle.engine.plugins

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi
import org.jetbrains.dokka.gradle.internal.addAll
import org.jetbrains.dokka.gradle.internal.putIfNotNull
import javax.inject.Inject


/**
 * Configuration for
 * [Dokka's Versioning plugin](https://kotl.in/dokka/versioning-plugin-readme).
 *
 * The versioning plugin provides the ability to host documentation for multiple versions of your
 * library/application with seamless switching between them. This, in turn, provides a better
 * experience for your users.
 *
 * Note: The versioning plugin only works with Dokka's HTML format.
 */
abstract class DokkaVersioningPluginParameters
@InternalDokkaGradlePluginApi
@Inject
constructor(
    name: String,
) : DokkaPluginParametersBaseSpec(
    name,
    DOKKA_VERSIONING_PLUGIN_FQN,
) {

    /**
     * The version of your application/library that documentation is going to be generated for.
     * This will be the version shown in the dropdown menu.
     */
    @get:Input
    @get:Optional
    abstract val version: Property<String>

    /**
     * An optional list of strings that represents the order that versions should appear in the
     * dropdown menu.
     *
     * Must match [version] string exactly. The first item in the list is at the top of the dropdown.
     * Any versions not in this list will be excluded from the dropdown.
     *
     * If no versions are supplied the versions will be ordered using SemVer ordering.
     */
    @get:Input
    @get:Optional
    abstract val versionsOrdering: ListProperty<String>

    /**
     * An optional path to a parent folder that contains other documentation versions.
     * It requires a specific directory structure.
     *
     * For more information, see
     * [Directory structure](https://kotl.in/dokka/versioning-plugin-readme#directory-structure).
     */
    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    @get:Optional
    abstract val olderVersionsDir: DirectoryProperty

    /**
     * An optional list of paths to other documentation versions. It must point to Dokka's outputs
     * directly. This is useful if different versions can't all be in the same directory.
     */
    @get:InputFiles
    @get:PathSensitive(RELATIVE)
    @get:Optional
    abstract val olderVersions: ConfigurableFileCollection

    /**
     * An optional folder name where other documentation versions output will be placed. In case
     * of empty string root folder will be used instead. Set to `older` by default.
     */
    @get:Input
    @get:Optional
    abstract val olderVersionsDirName: Property<String>

    /**
     * An optional boolean value indicating whether to render the navigation dropdown on all pages.
     *
     * Set to `true` by default.
     */
    @get:Input
    @get:Optional
    abstract val renderVersionsNavigationOnAllPages: Property<Boolean>

    override fun jsonEncode(): String {
        val versionsOrdering = versionsOrdering.orNull.orEmpty()

        return buildJsonObject {
            putIfNotNull("version", version.orNull)
            if (versionsOrdering.isNotEmpty()) {
                // only create versionsOrdering values are present, otherwise Dokka interprets
                // an empty list as "no versions, show nothing".
                putJsonArray("versionsOrdering") { addAll(versionsOrdering) }
            }
            putIfNotNull("olderVersionsDir", olderVersionsDir.orNull?.asFile)
            putJsonArray("olderVersions") {
                addAll(olderVersions.files)
            }
            putIfNotNull("olderVersionsDirName", olderVersionsDirName.orNull)
            putIfNotNull("renderVersionsNavigationOnAllPages", renderVersionsNavigationOnAllPages.orNull)
        }.toString()
    }

    companion object {
        const val DOKKA_VERSIONING_PLUGIN_PARAMETERS_NAME = "versioning"
        const val DOKKA_VERSIONING_PLUGIN_FQN = "org.jetbrains.dokka.versioning.VersioningPlugin"
    }
}

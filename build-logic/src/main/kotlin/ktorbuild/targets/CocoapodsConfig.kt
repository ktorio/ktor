/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

internal const val COCOAPODS_PLUGIN_ID = "org.jetbrains.kotlin.native.cocoapods"
internal const val COCOAPODS_BIN_PROPERTY = "kotlin.native.cocoapods.bin"

/**
 * Configures CocoaPods support when the CocoaPods Gradle plugin is applied.
 * Meant to be used together with the `ktorbuild.optional.cocoapods` plugin.
 */
fun KotlinMultiplatformExtension.optionalCocoapods(configure: CocoapodsExtension.() -> Unit) {
    val kotlin = this
    project.pluginManager.withPlugin(COCOAPODS_PLUGIN_ID) {
        (kotlin as ExtensionAware).extensions.configure<CocoapodsExtension>(configure)
    }
}

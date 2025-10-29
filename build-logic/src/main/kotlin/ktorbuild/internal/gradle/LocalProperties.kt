/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import org.gradle.api.Describable
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import java.util.*


internal val Project.localProperties: Provider<Map<String, String>>
    get() = providers
        .of(CustomPropertiesFileValueSource::class) {
            parameters.propertiesFile.set(project.rootDir.resolve("local.properties"))
        }

internal fun Project.localProperty(name: String): Provider<String> = localProperties.map { it[name] }

internal fun Provider<Map<String, String>>.hasKey(key: String): Provider<Boolean> =
    map { it.containsKey(key) }

// Copied from org.jetbrains.kotlin.gradle.plugin.internal.CustomPropertiesFileValueSource
internal abstract class CustomPropertiesFileValueSource : ValueSource<Map<String, String>, CustomPropertiesFileValueSource.Parameters>,
    Describable {

    interface Parameters : ValueSourceParameters {
        val propertiesFile: RegularFileProperty
    }

    override fun getDisplayName(): String = "properties file ${parameters.propertiesFile.get().asFile.absolutePath}"

    override fun obtain(): Map<String, String> {
        val customFile = parameters.propertiesFile.get().asFile
        return if (customFile.exists()) {
            customFile.bufferedReader().use {
                @Suppress("UNCHECKED_CAST")
                Properties().apply { load(it) }.toMap() as Map<String, String>
            }
        } else {
            emptyMap()
        }
    }
}

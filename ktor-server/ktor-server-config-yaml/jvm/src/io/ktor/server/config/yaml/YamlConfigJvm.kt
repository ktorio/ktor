/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exist; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.yaml.YamlConfig)
 */
@Suppress("ktlint:standard:function-naming")
public actual fun YamlConfig(path: String?): YamlConfig? {
    val resolvedPath = when {
        path == null -> DEFAULT_YAML_FILENAME
        path.endsWith(".yaml") -> path
        else -> return null
    }
    val resource = Thread.currentThread().contextClassLoader.getResource(resolvedPath)
    if (resource != null) {
        return resource.openStream().use {
            configFromString(String(it.readBytes()))
        }
    }
    val file = File(resolvedPath)
    if (file.exists()) {
        return configFromString(file.readText())
    }
    return null
}

private fun configFromString(content: String): YamlConfig {
    val yaml = Yaml.default.decodeFromString<YamlMap>(content)
    return YamlConfig.from(yaml)
}

internal actual fun getSystemPropertyOrEnvironmentVariable(key: String): String? {
    return System.getProperty(key) ?: System.getenv(key)
}

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.config.yaml

import io.ktor.server.config.*
import net.mamoe.yamlkt.*

internal const val DEFAULT_YAML_FILENAME = "application.yaml"

public class YamlConfigLoader : ConfigLoader {
    override fun load(path: String?): ApplicationConfig? {
        return YamlConfig(path)?.apply { checkEnvironmentVariables() }
    }
}

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exist; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 */
public expect fun YamlConfig(path: String?): YamlConfig?

/**
 * Implements [ApplicationConfig] by loading a configuration from a YAML file.
 * Values can reference to environment variables with `$ENV_VAR` syntax.
 */
public class YamlConfig(private val yaml: YamlMap) : ApplicationConfig {

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException("Path $path not found.")
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(yaml) { yaml, part -> yaml[part] as? YamlMap ?: return null }
        val value = yaml[parts.last()] ?: return null
        return ConfigValue(value, path)
    }

    override fun config(path: String): ApplicationConfig {
        val parts = path.split('.')
        val yaml = parts.fold(yaml) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        return YamlConfig(yaml)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(yaml) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        val value = yaml[parts.last()] as? YamlList ?: throw ApplicationConfigurationException("Path $path not found.")
        return value.map {
            YamlConfig(
                it as? YamlMap
                    ?: throw ApplicationConfigurationException("Property $path is not a list of maps.")
            )
        }
    }

    override fun keys(): Set<String> {
        fun keys(yaml: YamlMap): Set<String> {
            return yaml.keys.map { it.content as String }
                .flatMap { key ->
                    when (val value = yaml[key]) {
                        is YamlMap -> keys(value).map { "$key.$it" }
                        else -> listOf(key)
                    }
                }
                .toSet()
        }
        return keys(yaml)
    }

    public fun checkEnvironmentVariables() {
        fun check(element: YamlElement?) {
            when (element) {
                is YamlLiteral -> resolveValue(element.content)
                YamlNull -> return
                is YamlMap -> element.forEach { entry -> check(entry.value) }
                is YamlList -> element.forEach { check(it) }
                null -> return
            }
        }
        check(yaml)
    }

    private class ConfigValue(private val yaml: YamlElement, private val key: String) : ApplicationConfigValue {
        override fun getString(): String = yaml.asLiteralOrNull()?.content?.let { resolveValue(it) }
            ?: throw ApplicationConfigurationException("Property $key not found.")

        override fun getList(): List<String> = (yaml as? YamlList)
            ?.map { element ->
                element.asLiteralOrNull()?.content?.let { resolveValue(it) }
                    ?: throw ApplicationConfigurationException("Property $key is not a list of primitives.")
            }
            ?: throw ApplicationConfigurationException("Property $key not found.")
    }
}

private fun resolveValue(value: String): String {
    val isEnvVariable = value.startsWith("\$")
    if (!isEnvVariable) return value
    val key = value.drop(1)
    return getEnvironmentValue(key)
        ?: throw ApplicationConfigurationException("Environment variable \"$key\" not found")
}

internal expect fun getEnvironmentValue(key: String): String?

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import com.charleskorn.kaml.*
import io.ktor.server.config.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

internal const val DEFAULT_YAML_FILENAME = "application.yaml"

/**
 * Loads [ApplicationConfig] from a YAML file.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.yaml.YamlConfigLoader)
 */
public class YamlConfigLoader : ConfigLoader {
    /**
     * Tries loading an application configuration from the specified [path].
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.yaml.YamlConfigLoader.load)
     *
     * @return configuration or null if the path is not found or a configuration format is not supported.
     */
    override fun load(path: String?): ApplicationConfig? {
        return YamlConfig(path)
    }
}

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exists; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.yaml.YamlConfig)
 */
@Suppress("ktlint:standard:function-naming")
public expect fun YamlConfig(path: String?): YamlConfig?

/**
 * Implements [ApplicationConfig] by loading a configuration from a YAML file.
 * Values can reference to environment variables with `$ENV_VAR`, `${ENV_VAR}`, or `"$ENV_VAR:default_value"` syntax.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.yaml.YamlConfig)
 */
public class YamlConfig private constructor(yamlMap: YamlMap) : ApplicationConfig {
    internal companion object {
        internal fun from(yaml: YamlMap): YamlConfig =
            YamlConfig(yaml.swapEnvironmentVariables())

        private fun YamlNode.asConfigValueType(): ApplicationConfigValue.Type = when (this) {
            is YamlNull -> ApplicationConfigValue.Type.NULL
            is YamlScalar -> ApplicationConfigValue.Type.SINGLE
            is YamlList -> ApplicationConfigValue.Type.LIST
            is YamlMap -> ApplicationConfigValue.Type.OBJECT
            is YamlTaggedNode -> innerNode.asConfigValueType()
        }
    }
    private val rootNode: YamlMap = yamlMap
    private val format: Yaml = Yaml.default

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException("Path $path not found.")
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(rootNode) { yaml, part ->
            yaml[part] as? YamlMap ?: return null
        }
        val value: YamlNode = yaml[parts.last()] ?: return null
        if (value is YamlNull) return null
        if (value is YamlScalar && resolveReference(rootNode, value.content) == null) return null

        return YamlNodeConfigValue(path, value)
    }

    override fun config(path: String): ApplicationConfig {
        val parts = path.split('.')
        val yaml = parts.fold(rootNode) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        return YamlConfig(yaml)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(rootNode) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        val value = yaml[parts.last()] as? YamlList ?: throw ApplicationConfigurationException("Path $path not found.")
        return value.items.map {
            YamlConfig(
                it as? YamlMap
                    ?: throw ApplicationConfigurationException("Property $path is not a list of maps."),
            )
        }
    }

    override fun keys(): Set<String> {
        fun keys(yaml: YamlMap): Set<String> {
            return yaml.entries.entries
                .flatMap { (key, value) ->
                    when (value) {
                        is YamlMap -> keys(value).map { "${key.content}.$it" }
                        else -> listOf(key.content)
                    }
                }
                .toSet()
        }
        return keys(rootNode)
    }

    public override fun toMap(): Map<String, Any?> {
        val primitive = toPrimitive(rootNode, rootNode)
        @Suppress("UNCHECKED_CAST")
        return primitive as? Map<String, Any?> ?: error("Top level element is not a map")
    }

    @Deprecated("Redundant; handled automatically")
    public fun checkEnvironmentVariables() {
        fun check(element: YamlNode?) {
            when (element) {
                is YamlScalar -> resolveReference(rootNode, element.content)
                is YamlMap -> element.entries.forEach { entry -> check(entry.value) }
                is YamlList -> element.items.forEach { check(it) }
                else -> return
            }
        }
        check(rootNode)
    }

    private inner class YamlNodeConfigValue(
        private val key: String,
        private val node: YamlNode
    ) : ApplicationConfigValue {

        override val type: ApplicationConfigValue.Type
            get() = node.asConfigValueType()

        override fun getString(): String =
            (node as? YamlScalar)?.content?.let { value ->
                resolveReference(rootNode, value)
            } ?: throw ApplicationConfigurationException(
                "Failed to read property value for key as String: \"$key\""
            )

        override fun getList(): List<String> =
            (node as? YamlList)?.items?.let { list ->
                list.map { element ->
                    resolveReference(rootNode, element.yamlScalar.content)
                        ?: throw ApplicationConfigurationException(
                            "Failed to read element of property key as String: \"$key\""
                        )
                }
            } ?: throw ApplicationConfigurationException(
                "Failed to read property value for key as List<String>: \"$key\""
            )

        @Suppress("UNCHECKED_CAST")
        override fun getMap(): Map<String, Any?> =
            toPrimitive(rootNode, node) as? Map<String, Any?>
                ?: error("Expected map at $key but found ${type.name}")

        @OptIn(InternalAPI::class)
        override fun getAs(type: TypeInfo): Any? {
            return format.decodeFromYamlNode(type.serializer(), node)
        }
    }
}

private fun YamlMap.swapEnvironmentVariables(): YamlMap {
    val rootNode = this

    fun YamlNode.replace(): YamlNode =
        when (this) {
            is YamlList -> YamlList(items.map { it.replace() }, path)
            is YamlMap -> YamlMap(entries.mapValues { it.value.replace() }, path)
            is YamlScalar -> resolveReferences(rootNode)
            is YamlNull,
            is YamlTaggedNode -> this
        }

    return replace() as YamlMap
}

private fun YamlMap.deepReference(path: String): YamlNode? {
    val parts = path.split('.')
    val yaml = parts.dropLast(1).fold(this) { yaml, part ->
        yaml[part] as? YamlMap ?: return null
    }
    val value: YamlNode = yaml[parts.last()] ?: return null

    return value
}

private fun YamlScalar.resolveReferences(rootNode: YamlMap): YamlNode =
    resolveReference(rootNode, content)
        ?.let { YamlScalar(it, path) }
        ?: YamlNull(path)

private fun resolveReference(rootNode: YamlMap, value: String): String? {
    val isEnvVariable = value.startsWith("$")
    if (!isEnvVariable) return value

    val keyWithDefault = if (value.startsWith("\${") && value.endsWith("}")) {
        value.substring(2, value.length - 1)
    } else {
        value.drop(1)
    }

    val separatorIndex = keyWithDefault.indexOf(':')

    if (separatorIndex != -1) {
        val key = keyWithDefault.substring(0, separatorIndex)
        return getSystemPropertyOrEnvironmentVariable(key) ?: keyWithDefault.substring(separatorIndex + 1)
    }

    val selfReference = rootNode.deepReference(keyWithDefault)
    if (selfReference is YamlScalar) {
        return selfReference.content
    }

    val isOptional = keyWithDefault.first() == '?'
    val key = if (isOptional) keyWithDefault.drop(1) else keyWithDefault
    return getSystemPropertyOrEnvironmentVariable(key) ?: if (isOptional) {
        null
    } else {
        throw ApplicationConfigurationException(
            "Required environment variable \"$key\" not found and no default value is present"
        )
    }
}

private fun toPrimitive(rootNode: YamlMap, yaml: YamlNode?): Any? = when (yaml) {
    is YamlScalar -> resolveReference(rootNode, yaml.content)
    is YamlMap -> yaml.entries.entries.associate { (key, value) ->
        key.content to toPrimitive(rootNode, value)
    }
    is YamlList -> yaml.items.map { toPrimitive(rootNode, it) }
    is YamlNull -> null
    is YamlTaggedNode -> toPrimitive(rootNode, yaml.innerNode)
    null -> null
}

internal expect fun getSystemPropertyOrEnvironmentVariable(key: String): String?

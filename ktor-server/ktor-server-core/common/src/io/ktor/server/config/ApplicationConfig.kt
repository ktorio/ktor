/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

import io.ktor.server.application.*
import io.ktor.util.reflect.*

/**
 * Represents an application config node
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig)
 */
public interface ApplicationConfig {
    /**
     * Get config property with [path] or fail
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.property)
     *
     * @throws ApplicationConfigurationException
     */
    public fun property(path: String): ApplicationConfigValue

    /**
     * Get config property value for [path] or return `null`
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.propertyOrNull)
     */
    public fun propertyOrNull(path: String): ApplicationConfigValue?

    /**
     * Get config child node or fail
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.config)
     *
     * @throws ApplicationConfigurationException
     */
    public fun config(path: String): ApplicationConfig

    /**
     * Get a list of child nodes for [path] or fail
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.configList)
     *
     * @throws ApplicationConfigurationException
     */
    public fun configList(path: String): List<ApplicationConfig>

    /**
     * Returns the set of keys, found by recursing the root object.
     * All entries represent leaf nodes' keys, meaning that there would be no nested
     * objects directly included as values for returned keys.
     * It's still possible that entries may be a list and the lists may contain objects.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.keys)
     *
     * @return set of paths with non-null values, built up by recursing the entire tree of
     * config and creating an entry for each leaf value.
     */
    public fun keys(): Set<String>

    /**
     * Returns map representation of this config.
     * Values can be `String`, `Map<String, Any>`, `List<String>` and `List<Map<String, Any>>`
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfig.toMap)
     */
    public fun toMap(): Map<String, Any?>
}

/**
 * Represents an application config value
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue)
 */
public interface ApplicationConfigValue {
    /**
     * Represents the type of the application configuration value.
     *
     * The `kind` property indicates the structure or nature of the application configuration value,
     * which can assist in its processing or resolution. This property corresponds to the [Type] enum,
     * which defines the following possible values:
     * - `Single`: A single configuration value.
     * - `List`: A collection of multiple values.
     * - `Object`: A structured or nested configuration object.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.type)
     */
    public val type: Type

    /**
     * Get property string value
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.getString)
     */
    public fun getString(): String

    /**
     * Get property list value
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.getList)
     */
    public fun getList(): List<String>

    /**
     * Get property as a map
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.getMap)
     */
    public fun getMap(): Map<String, Any?>

    /**
     * Convert the property to an arbitrary type using deserialization.
     *
     * @param type the desired type of the return value provided by `typeInfo<T>()`
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.getAs)
     */
    public fun getAs(type: TypeInfo): Any?

    /**
     * Represents the type of application configuration value.
     *
     * `Type` enum outlines the structure or behavior of the config value, aiding in its resolution or manipulation.
     *
     * - `NULL`: Indicates an absence of value.
     * - `SINGLE`: Indicates a single value.
     * - `LIST`: Represents multiple values in list form.
     * - `OBJECT`: Represents a structured or nested configuration object.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigValue.Type)
     */
    public enum class Type {
        NULL,
        SINGLE,
        LIST,
        OBJECT
    }
}

/**
 * Convenience function for accessing properties using serialization.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.property)
 */
public inline fun <reified E> Application.property(key: String): E =
    environment.config.property(key).getAs()

/**
 * Convenience function for accessing properties using serialization.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.propertyOrNull)
 */
public inline fun <reified E> Application.propertyOrNull(key: String): E? =
    environment.config.propertyOrNull(key)?.getAs()

/**
 * Converts the application config value to the given type parameter.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.getAs)
 */
public inline fun <reified E> ApplicationConfigValue.getAs(): E =
    when (val typeInfo = typeInfo<E>()) {
        typeInfo<String>() -> getString() as E
        typeInfo<List<String>>() -> getList() as E
        typeInfo<Map<String, Any?>>() -> getMap() as E
        else -> getAs(typeInfo) as E
    }

/**
 * Thrown when an application is misconfigured
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ApplicationConfigurationException)
 */
public class ApplicationConfigurationException(message: String, cause: Throwable?) : Exception(message, cause) {
    public constructor(message: String) : this(message, null)
}

/**
 * Try read String value from [ApplicationConfig].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.tryGetString)
 *
 * @return null if key is missing
 */
public fun ApplicationConfig.tryGetString(key: String): String? =
    propertyOrNull(key)?.getString()

/**
 * Try read String value from [ApplicationConfig].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.tryGetStringList)
 *
 * @return null if key is missing
 */
public fun ApplicationConfig.tryGetStringList(key: String): List<String>? =
    propertyOrNull(key)?.getList()

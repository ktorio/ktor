/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.config

/**
 * Represents an application config node
 */
public interface ApplicationConfig {
    /**
     * Get config property with [path] or fail
     * @throws ApplicationConfigurationException
     */
    public fun property(path: String): ApplicationConfigValue

    /**
     * Get config property value for [path] or return `null`
     */
    public fun propertyOrNull(path: String): ApplicationConfigValue?

    /**
     * Get config child node or fail
     * @throws ApplicationConfigurationException
     */
    public fun config(path: String): ApplicationConfig

    /**
     * Get a list of child nodes for [path] or fail
     * @throws ApplicationConfigurationException
     */
    public fun configList(path: String): List<ApplicationConfig>
}

/**
 * Represents an application config value
 */
public interface ApplicationConfigValue {
    /**
     * Get property string value
     */
    public fun getString(): String

    /**
     * Get property list value
     */
    public fun getList(): List<String>
}

/**
 * Thrown when an application is misconfigured
 */
public class ApplicationConfigurationException(message: String) : Exception(message)

package io.ktor.config

interface ApplicationConfig {
    fun property(path: String): ApplicationConfigValue
    fun propertyOrNull(path: String): ApplicationConfigValue?
    fun config(path: String): ApplicationConfig
    fun configList(path: String): List<ApplicationConfig>
}

interface ApplicationConfigValue {
    fun getString(): String
    fun getList(): List<String>
}

class ApplicationConfigurationException(message: String) : Exception(message)

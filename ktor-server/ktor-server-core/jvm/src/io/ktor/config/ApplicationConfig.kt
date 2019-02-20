package io.ktor.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigMemorySize
import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Represents an application config node
 */
@KtorExperimentalAPI
interface ApplicationConfig {
    /**
     * Get config property with [path] or fail
     * @throws ApplicationConfigurationException
     */
    fun property(path: String): HoconApplicationConfigValue

    /**
     * Get config property value for [path] or return `null`
     */
    fun propertyOrNull(path: String): HoconApplicationConfigValue?

    /**
     * Get config child node or fail
     * @throws ApplicationConfigurationException
     */
    fun config(path: String): ApplicationConfig

    /**
     * Get a list of child nodes for [path] or fail
     * @throws ApplicationConfigurationException
     */
    fun configList(path: String): List<ApplicationConfig>
}

/**
 * Represents an application config value
 */
@KtorExperimentalAPI
class HoconApplicationConfigValue(private val config: Config, private val path: String) {
    /**
     * If the value is empty
     * @see [Config.isEmpty]
     */
    fun isEmpty(): Boolean = config.isEmpty

    /**
     * Get property [Boolean] value
     * @see [Config.getBoolean]
     */
    fun getBoolean(): Boolean = config.getBoolean(path)

    /**
     * Get property [Number] value
     * @see [Config.getNumber]
     */
    fun getNumber(): Number = config.getNumber(path)

    /**
     * Get property [Int] value
     * @see [Config.getInt]
     */
    fun getInt(): Int = config.getInt(path)

    /**
     * Get property [Long] value
     * @see [Config.getLong]
     */
    fun getLong(): Long = config.getLong(path)

    /**
     * Get property [Double] value
     * @see [Config.getDouble]
     */
    fun getDouble(): Double = config.getDouble(path)

    /**
     * Get property [String] value
     * @see [Config.getString]
     */
    fun getString(): String = config.getString(path)

    /**
     * Get property enum value
     * @param enumClass The type of enum to get the value as
     * @see [Config.getEnum]
     */
    fun <T : Enum<T>> getEnum(enumClass: KClass<T>): T = config.getEnum(enumClass.java, path)

    /**
     * Get property as [Any]
     * @see [Config.getAnyRef]
     */
    fun getAnyRef(): Any = config.getAnyRef(path)

    /**
     * Get property [ConfigMemorySize] value
     * @see [Config.getMemorySize]
     */
    fun getMemorySize(): ConfigMemorySize = config.getMemorySize(path)

    /**
     * Get property [Duration] value
     * @param timeUnit The [TimeUnit] to get the duration as
     * @see [Config.getDuration]
     */
    fun getDuration(timeUnit: TimeUnit): Long = config.getDuration(path, timeUnit)

    /**
     * Get property [Duration] value
     * @see [Config.getDuration]
     */
    fun getDuration(): Duration = config.getDuration(path)

    /**
     * Get property [Boolean] list value
     * @see [Config.getBooleanList]
     */
    fun getBooleanList(): List<Boolean> = config.getBooleanList(path)

    /**
     * Get property [Number] list value
     * @see [Config.getNumberList]
     */
    fun getNumberList(): List<Number> = config.getNumberList(path)

    /**
     * Get property [Int] list value
     * @see [Config.getIntList]
     */
    fun getIntList(): List<Int> = config.getIntList(path)

    /**
     * Get property [Long] list value
     * @see [Config.getLongList]
     */
    fun getLongList(): List<Long> = config.getLongList(path)

    /**
     * Get property [Double] list value
     * @see [Config.getDoubleList]
     */
    fun getDoubleList(): List<Double> = config.getDoubleList(path)

    @Deprecated(
        message = "Replace with 'getStringList()'",
        replaceWith = ReplaceWith("getStringList()", "io.ktor.config.ApplicationConfigValue.getStringList")
    )
    fun getList(): List<String> = config.getStringList(path)

    /**
     * Get property [String] list value
     * @see [Config.getStringList]
     */
    fun getStringList(): List<String> = config.getStringList(path)

    /**
     * Get property enum list value
     * @param enumClass The type of enum to get the value as
     * @see [Config.getEnumList]
     */
    fun <T : Enum<T>> getEnumList(enumClass: KClass<T>): List<T> = config.getEnumList(enumClass.java, path)

    /**
     * Get property as [List] of [Any]
     * @see [Config.getAnyRefList]
     */
    fun getAnyRefList(): List<Any> = config.getAnyRefList(path)

    /**
     * Get property [ConfigMemorySize] list value
     * @see [Config.getMemorySizeList]
     */
    fun getMemorySizeList(): List<ConfigMemorySize> = config.getMemorySizeList(path)

    /**
     * Get property [Duration] list value
     * @param timeUnit The [TimeUnit] to get the durations as
     * @see [Config.getDurationList]
     */
    fun getDurationList(timeUnit: TimeUnit): List<Long> = config.getDurationList(path, timeUnit)

    /**
     * Get property [Duration] list value
     * @see [Config.getDuration]
     */
    fun getDurationList(): List<Duration> = config.getDurationList(path)
}

@Suppress("unused")
@Deprecated("Replace with 'HoconApplicationConfigValue'", ReplaceWith("HoconApplicationConfigValue", "io.ktor.config"))
typealias ApplicationConfigValue = HoconApplicationConfigValue

/**
 * Thrown when an application is misconfigured
 */
@KtorExperimentalAPI
class ApplicationConfigurationException(message: String) : Exception(message)

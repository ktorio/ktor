package ktor.application

public class ConfigValueMissingException(desc: String) : RuntimeException(desc)

public trait Config {
    fun tryGet(name: String): String?
    /** Sets a value for the given key. */
    fun set(name: String, value: String): Unit

    /** Returns true if the config contains a value for the given key. */
    fun contains(name: String): Boolean = tryGet(name) != null
    fun get(name: String): String = tryGet(name) ?: throw ConfigValueMissingException("Could not find config value for key $name")
}
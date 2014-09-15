package ktor.application

import java.util.*

public class MemoryConfig(init : Config.()->Unit = {}) : Config {
    val properties = HashMap<String, String>();

    {
        init()
    }

    override fun tryGet(name: String): String? = properties.get(name)
    override fun set(name: String, value: String) {
        properties.put(name, value)
    }

    override fun toString(): String {
        val builder = StringBuilder("Configuration:\n")
        for ((name, value) in properties) {
            val filler = "                              "
            if (name.length >= filler.length)
                builder.append("  ${name} = ${value}\n")
            else
                builder.append("  ${name}${filler.substring(name.length)} = ${value}\n")
        }
        return builder.toString()
    }

}


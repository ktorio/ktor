package ktor.application

import javax.naming.*
import java.util.*

/**
 * Base class for config classes that use the Kara JSON config system.
 * Values are stored in a flat key/value map, and can be accessed like an array.
 */
public open class ContextConfig(val context: Context) : Config {

    public override fun tryGet(name: String): String? {
        try {
            val value = context.lookup(name)
            return value as String?
        } catch(e: NamingException) {
            return null
        }
    }

    public override fun set(name: String, value: String) {
        context.bind(name, value)
    }

    /** Prints the entire config to a nicely formatted string. */
    public override fun toString(): String {
        val builder = StringBuilder("Configuration:\n")
        for (item in context.list("").toList().sortBy { it.getName() }) {
            val name = item.getName()
            val filler = "                              "
            if (name.length >= filler.length)
                builder.append("  ${name} = ${get(name)}\n")
            else
                builder.append("  ${name}${filler.substring(name.length)} = ${get(name)}\n")
        }
        return builder.toString()
    }

}
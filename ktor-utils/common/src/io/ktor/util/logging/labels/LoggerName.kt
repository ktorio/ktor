/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import io.ktor.util.logging.*
import kotlin.reflect.*

/**
 * Creates new logger with the specified [name] (appended to parent's logger name).
 */
fun Logger.subLogger(name: String): Logger = fork {
    name(name)
}

/**
 * Specify logger name. Please note that if there are several names specified, all of them will be concatenated with dot.
 */
fun LoggingConfigBuilder.name(loggerName: String) {
    val suffix = ".$loggerName"
    var added = false

    @Suppress("UNCHECKED_CAST")
    val key = keys.filterIsInstance<LoggerNameKey>().lastOrNull() ?: LoggerNameKey().also {
        registerKey(it)
        added = true
    }

    enrich {
        val before = this[key]
        val after = when {
            before.isEmpty() -> loggerName
            else -> before + suffix
        }
        this[key] = after
    }

    if (added) {
        label {
            append(it[key])
        }
    }
}

/**
 * Creates new logger with name constructed of the [clazz] name
 */
fun Logger.forClass(clazz: KClass<*>): Logger {
    val name = clazz.getName() ?: return this

    return fork {
        name(name)
    }
}

/**
 * Creates new logger with name constructed of the [clazz] name
 */
inline fun <reified C : Any> Logger.forClass(): Logger = forClass(C::class)

/**
 * Creates new logger with name constructed of the class name
 */
inline fun <reified C : Any> (@Suppress("unused") C).loggerForClass(parent: Logger): Logger {
    return (parent ?: logger()).forClass<C>()
}

/**
 * Creates new logger with name constructed of the class name
 */
inline fun <reified C : Any> (@Suppress("unused") C).loggerForClass(config: Config = Config.Default): Logger {
    return logger(config).forClass<C>()
}

private class LoggerNameKey : LogAttributeKey<String>("logger-name", "")

internal expect fun KClass<*>.getName(): String?

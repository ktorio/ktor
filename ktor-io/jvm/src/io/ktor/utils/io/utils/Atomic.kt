package io.ktor.utils.io.utils

import java.util.concurrent.atomic.*
import kotlin.reflect.*

internal inline fun <reified Owner : Any> longUpdater(p: KProperty1<Owner, Long>): AtomicLongFieldUpdater<Owner> {
    return AtomicLongFieldUpdater.newUpdater(Owner::class.java, p.name)
}

internal fun getIOIntProperty(name: String, default: Int): Int =
        try { System.getProperty("kotlinx.io.$name") }
        catch (e: SecurityException) { null }
                ?.toIntOrNull() ?: default

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import sun.misc.Unsafe
import java.lang.reflect.Field

@Suppress("UNCHECKED_CAST")
internal object ConfigurationCacheWorkarounds {
    private val ignoreBeanFieldsField = Class.forName("org.gradle.internal.serialize.beans.services.Workarounds")
        .getDeclaredField("ignoredBeanFields")
        .apply { isAccessible = true }

    private val unsafe = runCatching {
        Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null) as Unsafe
    }.getOrNull()

    private var ignoreBeanFields = ignoreBeanFieldsField.get(null) as Array<Pair<String, String>>
        set(value) {
            val isSet = runCatching { unsafe?.setFinalStatic(ignoreBeanFieldsField, value) }.getOrNull() != null
            if (isSet) {
                field = value
            } else {
                // If we can't set a new array to the field, fallback to replacing array's content with new values
                for (i in 0..minOf(field.lastIndex, value.lastIndex)) field[i] = value[i]
            }
        }

    /** Registers fields that should be excluded from configuration cache. */
    fun addIgnoredBeanFields(vararg fields: Pair<String, String>) {
        ignoreBeanFields = fields as Array<Pair<String, String>> + ignoreBeanFields
    }
}

@Suppress("DEPRECATION")
private fun Unsafe.setFinalStatic(field: Field, value: Any) {
    val fieldBase = staticFieldBase(field)
    val fieldOffset = staticFieldOffset(field)

    putObject(fieldBase, fieldOffset, value)
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Returns all supertypes and interfaces implemented by the given type, including all
 * found in its hierarchy, such that all covariant types are included.
 *
 * When type arguments are encountered, they are included in parent type arguments, so
 * for example, `Collection<Element>` is included for the root type `ArrayList<Element>`.
 */
public actual fun TypeInfo.hierarchy(): List<TypeInfo> {
    val supertypes = kotlinType?.hierarchy() ?: type.supertypes
    return supertypes.mapNotNull { it.toTypeInfo() }
}

private fun KType.toTypeInfo(): TypeInfo? =
    (classifier as? KClass<*>)?.let {
        TypeInfo(
            type = classifier as KClass<*>,
            kotlinType = this
        )
    }

// List of generic classes to exclude from results
private val ignoredSupertypes = setOf(
    Any::class,
    java.io.Serializable::class,
    java.lang.Comparable::class,
    // Add more general types here as needed
    Object::class,
)

private fun KType.hierarchy(): List<KType> {
    // A helper function to substitute type arguments for supertypes
    fun substituteTypeArguments(supertype: KType, currentType: KType): KType {
        val currentTypeArguments = currentType.arguments
        val currentTypeClassifier = currentType.classifier as? KClass<*> ?: return supertype

        val substitutedArguments = supertype.arguments.map { projection ->
            when (val typeParameter = projection.type?.classifier) {
                is kotlin.reflect.KTypeParameter -> {
                    // Substitute type parameter with the actual type argument from the current type
                    val index = currentTypeClassifier.typeParameters.indexOf(typeParameter)
                    if (index >= 0) currentTypeArguments[index] else KTypeProjection.STAR
                }
                else -> projection // Keep as-is for non-parameter types
            }
        }

        // Create a new type with substituted arguments, or fallback to the original
        return supertype.classifier?.let { classifier ->
            (classifier as? KClass<*>)?.createType(substitutedArguments)
        } ?: supertype
    }

    // Recursive function to collect supertypes
    fun collectSupertypes(currentType: KType, collected: MutableSet<KType>) {
        val kClass = currentType.classifier as? KClass<*> ?: return

        for (supertype in kClass.supertypes) {
            val substitutedSupertype = substituteTypeArguments(supertype, currentType)
            val shouldIgnore = (substitutedSupertype.classifier as? KClass<*>) in ignoredSupertypes

            if (!shouldIgnore && collected.add(substitutedSupertype)) {
                collectSupertypes(substitutedSupertype, collected)
            }
        }
    }
    return mutableSetOf<KType>().also { set ->
        collectSupertypes(this, set)
    }.toList()
}

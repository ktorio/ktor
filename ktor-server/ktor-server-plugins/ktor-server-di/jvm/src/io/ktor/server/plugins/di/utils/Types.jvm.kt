/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType

/**
 * Returns all types of the hierarchy, starting with the given type.
 *
 * When type arguments are encountered, they are included in parent type arguments, so
 * for example, `Collection<Element>` is included for the root type `ArrayList<Element>`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.hierarchy)
 */
@InternalAPI
public actual fun TypeInfo.hierarchy(): Sequence<TypeInfo> {
    val supertypes = kotlinType?.hierarchy() ?: type.supertypes.asSequence()
    return supertypes.mapNotNull { it.toTypeInfo() }
}

/**
 * Converts the current [TypeInfo] into a nullable type representation.
 * If the type is already nullable, returns null.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.toNullable)
 *
 * @return A new [TypeInfo] instance with a nullable type, or null if the type is already nullable.
 */
@InternalAPI
public actual fun TypeInfo.toNullable(): TypeInfo? =
    kotlinType?.takeIf { !it.isMarkedNullable }?.let { kType ->
        TypeInfo(
            type,
            type.createType(
                arguments = kType.arguments,
                nullable = true,
                annotations = kType.annotations
            )
        )
    }

/**
 * Returns a list of the given base implementation for covariant type arguments.
 *
 * For example, supertype bounds with the `out` keyword will return matching supertypes for the type arguments.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.typeParametersHierarchy)
 *
 * @return A list of [TypeInfo] representing the base type with covariant type arguments.
 */
@InternalAPI
public actual fun TypeInfo.typeParametersHierarchy(): Sequence<TypeInfo> {
    // Return empty when not applicable
    if (!hasTypeParameters { it.variance == KVariance.OUT }) {
        return sequenceOf(this)
    }

    // Collect all possible supertypes for each OUT parameter
    val parameterSupertypes: Map<Int, List<KType>> = parameterSupertypes()
    val parameterIndices = parameterSupertypes.keys.toList()

    // Create a helper function to generate all combinations
    fun generateCombinations(
        currentIndex: Int,
        currentCombination: Map<Int, KType>
    ): Sequence<TypeInfo> =
        if (currentIndex >= parameterIndices.size) {
            // We have a complete combination, create a type with these arguments
            sequenceOf(this.parameterizedWith(currentCombination))
        } else {
            val paramIndex = parameterIndices[currentIndex]
            // Add each possible supertype for the current parameter to our combinations
            parameterSupertypes[paramIndex]!!.asSequence().flatMap { paramType ->
                generateCombinations(
                    currentIndex + 1,
                    currentCombination + (paramIndex to paramType)
                )
            }
        }

    // Generate all valid parameterized types from bounds
    return generateCombinations(0, emptyMap())
}

@InternalAPI
public fun TypeInfo.hasTypeParameters(predicate: (KTypeParameter) -> Boolean = { true }): Boolean {
    val kType = kotlinType ?: return false
    val typeParams = type.typeParameters
    if (typeParams.isEmpty()) return false
    if (typeParams.size != kType.arguments.size) return false
    return typeParams.any(predicate)
}

private fun TypeInfo.parameterSupertypes(): Map<Int, List<KType>> {
    val kotlinType = kotlinType!!
    val typeParams = type.typeParameters
    return buildMap {
        for ((index, argument) in kotlinType.arguments.withIndex()) {
            val typeParameter = typeParams[index]
            if (typeParameter.variance != KVariance.OUT) continue
            val argumentType = argument.type ?: continue
            val validSuperTypes = argumentType.hierarchy()
                .filter { it.isInBoundsOf(typeParameter) }
                .toList()

            put(index, validSuperTypes)
        }
    }
}

private fun TypeInfo.parameterizedWith(typeArgReplacements: Map<Int, KType>): TypeInfo {
    val swappedArguments = kotlinType!!.arguments.toMutableList()
    for ((paramIndex, paramType) in typeArgReplacements) {
        swappedArguments[paramIndex] = KTypeProjection(KVariance.INVARIANT, paramType)
    }
    return TypeInfo(
        type,
        type.createType(
            arguments = swappedArguments,
            annotations = type.annotations
        )
    )
}

private fun KType.toTypeInfo(): TypeInfo? =
    (classifier as? KClass<*>)?.let {
        TypeInfo(
            type = classifier as KClass<*>,
            kotlinType = this
        )
    }

private fun KType.isInBoundsOf(typeParameter: KTypeParameter): Boolean =
    typeParameter.upperBounds.all { upperBound ->
        val genericUpperBound = (upperBound.classifier as? KClass<*>)?.starProjectedType ?: upperBound
        genericUpperBound == this || genericUpperBound.isSupertypeOf(this)
    }

// List of generic classes to exclude from results
private val ignoredSupertypes = setOf(
    Any::class,
    Cloneable::class,
    java.io.Closeable::class,
    java.lang.AutoCloseable::class,
    java.io.Serializable::class,
    java.lang.Comparable::class,
    Enum::class,
    java.lang.Enum::class,
    Job::class,
    CoroutineContext::class,
    CoroutineContext.Element::class,
    Object::class,
    // Add more general types here as needed
)

private fun KType.hierarchy(): Sequence<KType> {
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
    return mutableSetOf(this).also { set ->
        collectSupertypes(this, set)
    }.asSequence()
}

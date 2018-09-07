package io.ktor.util

/**
 * API marked with this annotation is ktor internal and it is not intended to be used outside.
 * It could be modified or removed without any notice. Using it outside of ktor could cause undefined behaviour and/or
 * any strange effects.
 *
 * We are strongly recommend to not use such API.
 */
@Experimental(level = Experimental.Level.ERROR)
annotation class InternalAPI

/**
 * API marked with this annotation is experimental and is not guaranteed to be stable.
 */
@Experimental(level = Experimental.Level.WARNING)
annotation class KtorExperimentalAPI

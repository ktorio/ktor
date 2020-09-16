/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.pipeline

internal class PhaseContent<TSubject : Any, Call : Any>(
    val phase: PipelinePhase,
    val relation: PipelinePhaseRelation,
    private var interceptors: ArrayList<PipelineInterceptor<TSubject, Call>>
) {
    @Suppress("UNCHECKED_CAST")
    constructor(
        phase: PipelinePhase,
        relation: PipelinePhaseRelation
    ) : this(phase, relation, SharedArrayList as ArrayList<PipelineInterceptor<TSubject, Call>>) {
        check(SharedArrayList.isEmpty()) { "The shared empty array list has been modified" }
    }

    var shared: Boolean = true

    val isEmpty: Boolean get() = interceptors.isEmpty()
    val size: Int get() = interceptors.size

    fun addInterceptor(interceptor: PipelineInterceptor<TSubject, Call>) {
        if (shared) {
            copyInterceptors()
        }

        interceptors.add(interceptor)
    }

    fun addTo(destination: ArrayList<PipelineInterceptor<TSubject, Call>>) {
        val interceptors = interceptors
        destination.ensureCapacity(destination.size + interceptors.size)
        for (index in 0 until interceptors.size) {
            destination.add(interceptors[index])
        }
    }

    fun addTo(destination: PhaseContent<TSubject, Call>) {
        if (isEmpty) return

        if (destination.isEmpty) {
            destination.interceptors = sharedInterceptors()
            destination.shared = true
            return
        }

        if (destination.shared) {
            destination.copyInterceptors()
        }

        addTo(destination.interceptors)
    }

    fun sharedInterceptors(): ArrayList<PipelineInterceptor<TSubject, Call>> {
        shared = true
        return interceptors
    }

    fun copiedInterceptors(): ArrayList<PipelineInterceptor<TSubject, Call>> = ArrayList(interceptors)

    override fun toString(): String = "Phase `${phase.name}`, $size handlers"

    private fun copyInterceptors() {
        interceptors = copiedInterceptors()
        shared = false
    }

    companion object {
        val SharedArrayList = ArrayList<Any?>(0)
    }
}
